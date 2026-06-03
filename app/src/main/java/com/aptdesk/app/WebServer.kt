package com.aptdesk.app

import android.content.Context
import android.os.SystemClock
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class WebServer(
    private val context: Context,
    private val prootManager: ProotManager,
    port: Int = 8082
) : NanoHTTPD("127.0.0.1", port) {

    private val rootfsDir = File(context.filesDir, "rootfs")

    @Volatile private var cpuPercent = -1
    @Volatile private var prevCpuTime = 0L
    @Volatile private var prevWallTime = 0L
    private var cpuPollThread: Thread? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/api/status" -> handleStatus()
            uri == "/api/sessions" -> handleSessions()
            uri == "/api/restart" -> handleRestart(session)
            uri == "/api/software/list" -> handleSoftwareList()
            uri == "/api/software/search" -> handleSoftwareSearch(session)
            uri == "/api/software/action" -> handleSoftwareAction(session)
            uri.startsWith("/api/files/") || uri == "/api/files" -> handleFiles(uri)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found"
            )
        }
    }

    private fun handleStatus(): Response {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memInfo = android.app.ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            
            val statFs = android.os.StatFs(context.filesDir.path)
            val totalDisk = statFs.totalBytes
            val freeDisk = statFs.availableBytes
            val usedDisk = totalDisk - freeDisk

            val status = JSONObject().apply {
                put("status", if (prootManager.isRunning()) "running" else "stopped")
                put("ip", NetworkInfo.getLocalIpAddress())
                
                val ram = JSONObject().apply {
                    put("total", String.format("%.2f", memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)))
                    put("used", String.format("%.2f", (memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0 * 1024.0)))
                }
                put("ram", ram)
                
                val disk = JSONObject().apply {
                    put("total", String.format("%.2f", totalDisk / (1024.0 * 1024.0 * 1024.0)))
                    put("used", String.format("%.2f", usedDisk / (1024.0 * 1024.0 * 1024.0)))
                }
                put("disk", disk)
                
                val cpuVal = readCpuUsage()
                put("cpu", if (cpuVal >= 0) cpuVal else JSONObject.NULL)
                put("battery", getBatteryInfo())
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                status.toString()
            )
        } catch (e: Exception) {
            android.util.Log.e("AptDeskWebServer", "Error in handleStatus", e)
            val fallback = JSONObject().apply {
                put("status", "error")
                put("error", e.message)
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", fallback.toString())
        }
    }

    private fun handleSessions(): Response {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("ps", "-A", "-o", "etime,comm"))
            val output = process.inputStream.bufferedReader().readText()
            
            val sessionsArray = org.json.JSONArray()
            
            var ttydUptime = "-"
            var ttydStatus = "Inactive"
            var ttydBadge = "neutral"
            
            var vncUptime = "-"
            var vncStatus = "Inactive"
            var vncBadge = "neutral"
            
            var filesUptime = "-"
            var filesStatus = "Inactive"
            var filesBadge = "neutral"

            output.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    val parts = trimmed.split(Regex("\\s+"), 2)
                    if (parts.size == 2) {
                        val etime = parts[0]
                        val comm = parts[1]
                        
                        if (comm.contains("ttyd")) {
                            ttydUptime = etime
                            ttydStatus = "Active"
                            ttydBadge = "success"
                        } else if (comm.contains("Xvnc") || comm.contains("Xtiger")) {
                            vncUptime = etime
                            vncStatus = "Active"
                            vncBadge = "success"
                        } else if (comm.contains("filebrowser")) {
                            filesUptime = etime
                            filesStatus = "Active"
                            filesBadge = "success"
                        }
                    }
                }
            }

            sessionsArray.put(JSONObject().apply {
                put("name", "desktop-01")
                put("user", "vncserver")
                put("uptime", vncUptime)
                put("status", vncStatus)
                put("badge", vncBadge)
            })
            
            sessionsArray.put(JSONObject().apply {
                put("name", "terminal-02")
                put("user", "ttyd")
                put("uptime", ttydUptime)
                put("status", ttydStatus)
                put("badge", ttydBadge)
            })
            
            sessionsArray.put(JSONObject().apply {
                put("name", "files-sync")
                put("user", "filebrowser")
                put("uptime", filesUptime)
                put("status", filesStatus)
                put("badge", filesBadge)
            })

            return newFixedLengthResponse(Response.Status.OK, "application/json", sessionsArray.toString())
        } catch (e: Exception) {
            android.util.Log.e("AptDeskWebServer", "Error in handleSessions", e)
            val fallback = org.json.JSONArray()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", fallback.toString())
        }
    }

    private fun handleSoftwareList(): Response {
        try {
            val output = prootManager.executeCommand("/usr/bin/dpkg-query -W -f='\${Package}|||\${Version}|||\${Status}\n'")
            val softwareArray = org.json.JSONArray()
            
            output.lines().forEach { line ->
                val parts = line.split("|||")
                if (parts.size >= 3) {
                    val name = parts[0].trim()
                    val version = parts[1].trim()
                    val statusStr = parts[2].trim()
                    
                    if (name.isNotEmpty() && statusStr.contains("installed")) {
                        softwareArray.put(JSONObject().apply {
                            put("name", name)
                            put("version", version)
                            put("status", "Installed")
                        })
                    }
                }
            }
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", softwareArray.toString())
        } catch (e: Exception) {
            val fallback = org.json.JSONArray()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", fallback.toString())
        }
    }

    private fun handleSoftwareSearch(session: IHTTPSession): Response {
        try {
            val query = session.parameters["q"]?.firstOrNull() ?: ""
            if (query.trim().isEmpty() || !query.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "[]")
            }
            
            val installedOutput = prootManager.executeCommand("/usr/bin/dpkg-query -W -f='\${Package}|||\${Status}\n'")
            val installedSet = mutableSetOf<String>()
            installedOutput.lines().forEach { line ->
                val parts = line.split("|||")
                if (parts.size >= 2) {
                    if (parts[1].contains("installed")) {
                        installedSet.add(parts[0].trim())
                    }
                }
            }
            
            val output = prootManager.executeCommand("/usr/bin/apt-cache search $query")
            val softwareArray = org.json.JSONArray()
            
            output.lines().take(50).forEach { line ->
                val parts = line.split(" - ", limit = 2)
                if (parts.size == 2) {
                    val name = parts[0].trim()
                    val desc = parts[1].trim()
                    
                    if (name.isNotEmpty()) {
                        softwareArray.put(JSONObject().apply {
                            put("name", name)
                            put("version", desc) // Using version column for description in search
                            put("status", if (installedSet.contains(name)) "Installed" else "Available")
                        })
                    }
                }
            }
            
            return newFixedLengthResponse(Response.Status.OK, "application/json", softwareArray.toString())
        } catch (e: Exception) {
            val fallback = org.json.JSONArray()
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", fallback.toString())
        }
    }

    private fun handleSoftwareAction(session: IHTTPSession): Response {
        try {
            if (session.method != Method.POST) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, "application/json", "{\"error\":\"POST required\"}")
            }
            session.parseBody(null)
            val pkg = session.parameters["pkg"]?.firstOrNull() ?: ""
            val action = session.parameters["action"]?.firstOrNull() ?: ""
            
            if (pkg.trim().isEmpty() || !pkg.matches(Regex("^[a-zA-Z0-9_.-]+$"))) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Invalid package\"}")
            }
            
            val cmd = if (action == "install") {
                "/usr/bin/apt-get install -y $pkg"
            } else if (action == "remove") {
                "/usr/bin/apt-get remove -y $pkg"
            } else {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", "{\"error\":\"Invalid action\"}")
            }
            
            val output = prootManager.executeCommand("DEBIAN_FRONTEND=noninteractive $cmd")
            val isSuccess = output.contains("Setting up $pkg") || output.contains("Removing $pkg") || output.contains("is already the newest version") || output.contains("newly installed")
            
            // To be totally safe, if it didn't crash and has no E: lines, it might be fine.
            return newFixedLengthResponse(Response.Status.OK, "application/json", JSONObject().apply {
                put("success", !output.contains("E: "))
                put("log", output)
            }.toString())
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json", "{\"error\":\"${e.message}\"}")
        }
    }

    private fun handleRestart(session: IHTTPSession): Response {
        if (session.method != Method.POST) {
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                MIME_PLAINTEXT,
                "POST required"
            )
        }
        
        try {
            prootManager.stop()
            prootManager.start()
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                """{"status":"restarted"}"""
            )
        } catch (e: Exception) {
             return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }

    override fun start() {
        super.start()
        startCpuPolling()
    }

    override fun stop() {
        cpuPollThread?.interrupt()
        cpuPollThread = null
        super.stop()
    }

    private fun startCpuPolling() {
        cpuPollThread = Thread {
            try { pollCpu() } catch (_: Exception) {}
            try { Thread.sleep(800) } catch (_: InterruptedException) { return@Thread }
            try { pollCpu() } catch (_: Exception) {}

            while (!Thread.currentThread().isInterrupted) {
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
                try { pollCpu() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true; name = "cpu-poll"; start() }
    }

    private fun pollCpu() {
        val result = tryAppCpuUsage()
        Log.d("AptDeskWebServer", "pollCpu result=$result, cpuPercent=$cpuPercent")
        if (result != null) cpuPercent = result
    }

    private fun tryAppCpuUsage(): Int? {
        return try {
            val stat = File("/proc/self/stat").readText().trim()
            val parts = stat.split(" ")
            val utime = parts.getOrNull(13)?.toLongOrNull() ?: return null
            val stime = parts.getOrNull(14)?.toLongOrNull() ?: return null
            val cpuTime = utime + stime

            val wallTime = SystemClock.elapsedRealtime()

            val prevCpu = prevCpuTime
            val prevWall = prevWallTime

            prevCpuTime = cpuTime
            prevWallTime = wallTime

            if (prevWall > 0 && wallTime > prevWall) {
                val deltaCpu = cpuTime - prevCpu
                val deltaWall = wallTime - prevWall
                val pct = ((deltaCpu * 10L * 100L) / deltaWall).toInt()
                Log.d("AptDeskWebServer", "appCPU: $pct% (deltaCpu=$deltaCpu deltaWall=$deltaWall)")
                return pct.coerceIn(0, 100)
            }
            null
        } catch (e: Exception) {
            Log.d("AptDeskWebServer", "tryAppCpuUsage failed: ${e.message}")
            null
        }
    }

    private fun getBatteryInfo(): JSONObject {
        val intent = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val level = intent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100) ?: 100
        val tempRaw = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val status = intent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1

        val percent = if (scale > 0) (level * 100 / scale) else level
        val temp = tempRaw / 10.0
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == android.os.BatteryManager.BATTERY_STATUS_FULL

        return JSONObject().apply {
            put("percent", percent)
            put("temp", String.format("%.1f", temp))
            put("charging", charging)
        }
    }

    private fun readCpuUsage(): Int = cpuPercent

    private fun handleFiles(uri: String): Response {
        val path = uri.removePrefix("/api/files").removePrefix("/")
        val target = File(rootfsDir, path)

        try {
            if (!target.canonicalPath.startsWith(rootfsDir.canonicalPath)) {
                 return newFixedLengthResponse(
                    Response.Status.FORBIDDEN,
                    MIME_PLAINTEXT,
                    "Access Denied"
                )
            }
        } catch (e: IOException) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Path resolution failed"
            )
        }

        if (!target.exists()) {
             return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "File Not Found"
            )
        }

        if (target.isDirectory) {
            val files = target.listFiles() ?: emptyArray()
            val array = JSONArray()
            files.forEach { file ->
                array.put(JSONObject().apply {
                    put("name", file.name)
                    put("isDirectory", file.isDirectory)
                    put("size", file.length())
                })
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                array.toString()
            )
        } else {
            return try {
                val fis = FileInputStream(target)
                newChunkedResponse(
                    Response.Status.OK,
                    "application/octet-stream",
                    fis
                )
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR,
                    MIME_PLAINTEXT,
                    "Error reading file"
                )
            }
        }
    }
}
