package com.aptdesk.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class ProotManager(private val context: Context) {
    private val rootfsDir = File(context.filesDir, "rootfs")
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val prootBinary = File(nativeDir, "libproot.so")
    private val prootLoader = File(nativeDir, "libproot-loader.so")
    private var process: Process? = null
    private var logThread: Thread? = null

    @Throws(IOException::class, IllegalStateException::class)
    fun start(resolution: String = "1280x720") {
        if (process?.isAlive == true) {
            return
        }
        if (!rootfsDir.exists()) {
            throw IllegalStateException("Rootfs directory missing: ${rootfsDir.path}")
        }
        ensureBinary(prootBinary, "libproot.so")
        ensureBinary(prootLoader, "libproot-loader.so")

        val sharedDir = File(context.filesDir, "shared")
        if (!sharedDir.exists() && !sharedDir.mkdirs()) {
            throw IOException("Failed to create shared directory: ${sharedDir.path}")
        }

        val command = listOf(
            prootBinary.absolutePath,
            "-0",
            "--kill-on-exit",
            "--link2symlink",
            "-b", "/dev:/dev",
            "-b", "/proc:/proc",
            "-b", "/sys:/sys",
            "-b", "${sharedDir.path}:/shared",
            "--rootfs", rootfsDir.path,
            "/bin/bash", "-c", startupScript(resolution)
        )

        process = ProcessBuilder(command).apply {
            environment()["PROOT_TMP_DIR"] = context.cacheDir.path
            environment()["PROOT_LOADER"] = prootLoader.absolutePath
            redirectErrorStream(true)
        }.start()

        startLogPump(process)
    }

    fun stop() {
        process?.destroy()
        process = null
        logThread?.interrupt()
        logThread = null
    }

    fun isRunning(): Boolean = process?.isAlive == true

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

    private fun startupScript(resolution: String) = """
        #!/bin/bash
        export DISPLAY=:0
        export HOME=/root
        export LANG=en_US.UTF-8

        Xvfb :0 -screen 0 ${resolution}x24 -ac &
        while ! xdpyinfo -display :0 >/dev/null 2>&1; do
            sleep 0.5
        done

        startxfce4 &
        x0vncserver -display :0 -rfbport 5900 -SecurityTypes None &
        websockify --web=/opt/aptdesk/www/vnc 5901 127.0.0.1:5900 &
        ttyd -p 8081 -W /bin/bash &
        caddy run --config /opt/aptdesk/Caddyfile &

        wait
    """.trimIndent()

    companion object {
        private const val TAG = "ProotManager"
    }
}
