package com.aptdesk.app

import android.content.Context
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

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return when {
            uri == "/api/status" -> handleStatus()
            uri == "/api/restart" -> handleRestart(session)
            uri.startsWith("/api/files/") || uri == "/api/files" -> handleFiles(uri)
            else -> newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found"
            )
        }
    }

    private fun handleStatus(): Response {
        val status = JSONObject().apply {
            put("status", if (prootManager.isRunning()) "running" else "stopped")
            put("ip", NetworkInfo.getLocalIpAddress())
        }
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json",
            status.toString()
        )
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
