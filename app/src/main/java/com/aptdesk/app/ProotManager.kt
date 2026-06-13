package com.aptdesk.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

class ProotManager(
    private val context: Context,
    private val processBuilderFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }
) {
    private val rootfsDir = File(context.filesDir, "rootfs")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val prootBinary = File(nativeDir, "libproot.so")
    private val prootLoader = File(nativeDir, "libproot-loader.so")
    // liblibtalloc2.so is the Android-legal name for libtalloc.so.2 in jniLibs.
    // At runtime we copy it to filesDir as "libtalloc.so.2" so the dynamic linker
    // can find it by the exact SONAME that libproot.so requires.
    private val libsDir = File(context.filesDir, "libs")
    private var process: Process? = null
    private var virglProcess: Process? = null
    private var logThread: Thread? = null

    @Throws(IOException::class, IllegalStateException::class)
    fun start(resolution: String = "1280x720", enableGpu: Boolean = true) {
        if (process?.isAlive == true) {
            return
        }
        if (!rootfsDir.exists()) {
            throw IllegalStateException("Rootfs directory missing: ${rootfsDir.path}")
        }
        ensureBinary(prootBinary, "libproot.so")
        ensureBinary(prootLoader, "libproot-loader.so")
        ensureTallocLib()

        val sharedDir = File(context.filesDir, "shared")
        sharedDir.mkdirs()
        
        val shmDir = File(context.filesDir, "shm")
        shmDir.mkdirs()

        // Handle VirGL host rendering server
        val virglBinary = File(nativeDir, "libvirgl_test_server.so")
        var gpuActive = enableGpu
        if (enableGpu) {
            if (!virglBinary.exists()) {
                Log.w(TAG, "libvirgl_test_server.so not found, falling back to software rendering")
                gpuActive = false
            } else {
                try {
                    ensureBinary(virglBinary, "libvirgl_test_server.so")
                    val virglSharedDir = File(context.cacheDir, "virgl-shared").apply { mkdirs() }
                    // Clean up stale socket file to prevent bind address in use errors
                    File(virglSharedDir, ".virgl_test").delete()

                    virglProcess = processBuilderFactory(listOf(
                        virglBinary.absolutePath,
                        "--socket-path", File(virglSharedDir, ".virgl_test").absolutePath,
                        "--multi-clients"
                    )).apply {
                        redirectErrorStream(true)
                        environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
                    }.start()
                    Log.i(TAG, "Started virgl_test_server at ${virglSharedDir.absolutePath}/.virgl_test")
                    startVirglLogPump(virglProcess)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start virgl_test_server, falling back to software rendering", e)
                    gpuActive = false
                }
            }
        }

        val command = mutableListOf(
            prootBinary.absolutePath,
            "-0",
            "--kill-on-exit",
            "--link2symlink",
            "-b", "/dev:/dev",
            "-b", "/proc:/proc",
            "-b", "/sys:/sys",
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${shmDir.absolutePath}:/dev/shm"
        )

        if (gpuActive) {
            val virglSharedDir = File(context.cacheDir, "virgl-shared")
            command.add("-b")
            command.add("${virglSharedDir.absolutePath}:/tmp")
        }

        command.add("--rootfs=${rootfsDir.absolutePath}")
        command.add("/bin/bash")
        command.add("-c")
        command.add(startupScript(resolution, gpuActive))

        process = processBuilderFactory(command).apply {
            environment()["PROOT_TMP_DIR"] = context.cacheDir.path
            environment()["PROOT_LOADER"] = prootLoader.absolutePath
            // Prepend our libs dir so the linker finds libtalloc.so.2 before
            // looking in the Termux RUNPATH which doesn't exist on this device.
            val existingPath = environment()["LD_LIBRARY_PATH"] ?: ""
            environment()["LD_LIBRARY_PATH"] = if (existingPath.isEmpty()) {
                libsDir.absolutePath
            } else {
                "${libsDir.absolutePath}:$existingPath"
            }
            redirectErrorStream(true)
        }.start()

        startLogPump(process)
    }

    fun stop() {
        process?.destroy()
        process = null
        virglProcess?.destroy()
        virglProcess = null
        logThread?.interrupt()
        logThread = null
        killOrphanedProcesses()
    }

    private fun killOrphanedProcesses() {
        val cmds = listOf("Xvfb", "x0vncserver", "websockify", "caddy", "ttyd", "filebrowser", "xfce4-session", "libvirgl_test_server.so")
        for (cmd in cmds) {
            try {
                // Use toybox/toolbox pkill on Android host to clean up orphaned processes
                Runtime.getRuntime().exec(arrayOf("pkill", "-9", "-f", cmd)).waitFor()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to kill $cmd", e)
            }
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true

    private fun startVirglLogPump(process: Process?) {
        if (process == null) return
        Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> Log.i("VirglServer", line) }
                }
            } catch (e: IOException) {
                Log.w("VirglServer", "Log stream closed", e)
            }
            if (!process.isAlive) {
                Log.w("VirglServer", "Process exited with code ${process.exitValue()}")
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun startLogPump(process: Process?) {
        if (process == null) {
            return
        }
        val thread = Thread {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> Log.i(TAG, line) }
                }
            } catch (error: IOException) {
                Log.w(TAG, "Log stream closed", error)
            }
        }
        thread.isDaemon = true
        thread.start()
        logThread = thread
    }

    private fun ensureTallocLib() {
        if (!libsDir.exists() && !libsDir.mkdirs()) {
            throw IOException("Failed to create libs dir: ${libsDir.path}")
        }
        val dest = File(libsDir, "libtalloc.so.2")
        if (dest.exists()) return  // already staged

        val src = File(nativeDir, "liblibtalloc2.so")
        if (!src.exists()) {
            throw IllegalStateException("Missing liblibtalloc2.so in native libs — libtalloc staging failed")
        }
        FileInputStream(src).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        Log.i(TAG, "Staged libtalloc.so.2 → ${dest.path}")
    }

    private fun ensureBinary(file: File, name: String) {
        if (!file.exists()) {
            throw IllegalStateException("Missing $name in native libs: ${file.path}")
        }
        // nativeLibraryDir is mounted read-only by the Android system; setExecutable()
        // would throw here. With android:extractNativeLibs="true" the OS already sets
        // the executable bit during APK installation, so no manual chmod is needed.
        if (!file.canExecute()) {
            throw IllegalStateException("$name is not executable (unexpected on API 29+): ${file.path}")
        }
    }

    private fun startupScript(resolution: String, enableGpu: Boolean) = """
        #!/bin/bash
        export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
        export DISPLAY=:0
        export HOME=/root
        export LANG=en_US.UTF-8
        export TMPDIR=/tmp
        export XDG_RUNTIME_DIR=/tmp

        ${if (enableGpu) """
        # Enable VirGL GPU Acceleration overrides
        export GALLIUM_DRIVER=virpipe
        export MESA_GL_VERSION_OVERRIDE=3.3
        export MESA_GLES_VERSION_OVERRIDE=3.0
        """ else ""}

        # QtWebEngine/Chromium apps often need this to not crash immediately in PRoot
        export QTWEBENGINE_DISABLE_SANDBOX=1

        # Fix DNS resolution (Ubuntu defaults to systemd-resolved which isn't running)
        rm -f /etc/resolv.conf
        echo "nameserver 8.8.8.8" > /etc/resolv.conf
        echo "nameserver 1.1.1.1" >> /etc/resolv.conf

        # Fix x0vncserver hostname resolution crash
        echo "127.0.0.1 localhost $(hostname 2>/dev/null || echo aptdesk)" > /etc/hosts

        Xvfb :0 -screen 0 ${resolution}x24 -ac &
        
        # Wait up to 10 seconds for X11 to start (20 * 0.5s)
        for i in {1..20}; do
            xdpyinfo -display :0 >/dev/null 2>&1 && break
            sleep 0.5
        done

        # Pre-configure XFCE to disable compositing before starting to prevent GPU window/border glitches in VNC/Xvfb
        mkdir -p /root/.config/xfce4/xfconf/xfce-perchannel-xml
        cat <<'EOF' > /root/.config/xfce4/xfconf/xfce-perchannel-xml/xfwm4.xml
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="use_compositing" type="bool" value="false"/>
  </property>
</channel>
EOF

        # Also attempt disabling dynamically as a fallback
        (sleep 3 && xfconf-query -c xfwm4 -p /general/use_compositing -s false) &

        startxfce4 >/var/log/xfce.log 2>&1 &
        x0vncserver -display :0 -rfbport 5900 -SecurityTypes None >/var/log/x0vncserver.log 2>&1 &
        websockify --web=/opt/aptdesk/www/libs/novnc 5901 127.0.0.1:5900 &
        ttyd -p 8081 /bin/bash >/var/log/ttyd.log 2>&1 &
        
        # Start filebrowser
        chmod +x /opt/aptdesk/www/bin/filebrowser
        
        /opt/aptdesk/www/bin/filebrowser -b /filesapp -p 8083 -r / -d /var/lib/filebrowser.db -a 127.0.0.1 --noauth >/var/log/filebrowser.log 2>&1 &
        
        # Strip Windows CRLF from Caddyfile before parsing
        sed -i 's/\r//' /opt/aptdesk/Caddyfile
        caddy run --config /opt/aptdesk/Caddyfile >/var/log/caddy.log 2>&1 &

        wait
    """.trimIndent()

    fun executeCommand(cmd: String): String {
        if (!rootfsDir.exists()) return "Error: Rootfs missing"
        ensureBinary(prootBinary, "libproot.so")
        ensureBinary(prootLoader, "libproot-loader.so")
        ensureTallocLib()

        val command = listOf(
            prootBinary.absolutePath,
            "-0",
            "--link2symlink",
            "-b", "/dev:/dev",
            "-b", "/proc:/proc",
            "-b", "/sys:/sys",
            "--rootfs=${rootfsDir.absolutePath}",
            "/bin/bash", "-c", cmd
        )
        try {
            val proc = processBuilderFactory(command).apply {
                environment()["PROOT_TMP_DIR"] = context.cacheDir.path
                environment()["PROOT_LOADER"] = prootLoader.absolutePath
                val existingPath = environment()["LD_LIBRARY_PATH"] ?: ""
                environment()["LD_LIBRARY_PATH"] = if (existingPath.isEmpty()) {
                    libsDir.absolutePath
                } else {
                    "${libsDir.absolutePath}:$existingPath"
                }
                redirectErrorStream(true)
            }.start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (proc.isAlive) {
                proc.destroyForcibly()
                return "Error: Command timed out after 120 seconds."
            }
            return output
        } catch(e: Exception) {
            return "Error executing command: ${e.message}"
        }
    }

    companion object {
        private const val TAG = "ProotManager"
    }
}
