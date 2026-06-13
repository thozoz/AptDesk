package com.aptdesk.app

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WebServerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var prootManager: ProotManager
    private lateinit var filesDir: File
    private lateinit var rootfsDir: File
    private lateinit var webServer: WebServer
    private var port: Int = 0

    @Before
    fun setUp() {
        AptDeskState.reset()
        filesDir = tempFolder.newFolder("files")
        rootfsDir = File(filesDir, "rootfs")
        rootfsDir.mkdirs()

        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir

        prootManager = mockk(relaxed = true)

        // Bind to port 0 for ephemeral port allocation
        webServer = WebServer(context, prootManager, 0)
        webServer.start()
        port = webServer.listeningPort
    }

    @After
    fun tearDown() {
        if (::webServer.isInitialized) {
            webServer.stop()
        }
    }

    private data class ResponseData(val status: Int, val body: String)

    private fun sendRequest(method: String, path: String, queryParams: String? = null, postBody: String? = null): ResponseData {
        val urlString = if (queryParams != null) {
            "http://127.0.0.1:$port$path?$queryParams"
        } else {
            "http://127.0.0.1:$port$path"
        }
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.doInput = true

        if (method == "POST") {
            conn.doOutput = true
            val bytes = postBody?.toByteArray() ?: ByteArray(0)
            conn.outputStream.use { it.write(bytes) }
        }

        val status = conn.responseCode
        val content = try {
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            stream?.bufferedReader()?.readText() ?: ""
        } catch (e: Exception) {
            ""
        }
        return ResponseData(status, content)
    }

    @Test
    fun testHandleFilesPathTraversalForbidden() {
        // Attempt traversal outside rootfs
        val resp = sendRequest("GET", "/api/files/../../../etc/passwd")
        assertEquals(403, resp.status)
        assertEquals("Access Denied", resp.body)

        val resp2 = sendRequest("GET", "/api/files/rootfs/../../outside")
        assertEquals(403, resp2.status)
        assertEquals("Access Denied", resp2.body)
    }

    @Test
    fun testHandleFilesNormalFile() {
        File(rootfsDir, "test.txt").apply { writeText("Hello AptDesk!") }
        val resp = sendRequest("GET", "/api/files/test.txt")
        assertEquals(200, resp.status)
        assertEquals("Hello AptDesk!", resp.body)
    }

    @Test
    fun testHandleFilesDirectoryListing() {
        val subDir = File(rootfsDir, "subdir").apply { mkdirs() }
        File(subDir, "file1.txt").apply { writeText("content1") }
        File(subDir, "file2.txt").apply { writeText("content2") }

        val resp = sendRequest("GET", "/api/files/subdir")
        assertEquals(200, resp.status)

        val jsonArray = JSONArray(resp.body)
        assertEquals(2, jsonArray.length())

        val file1 = jsonArray.getJSONObject(0)
        assertEquals("file1.txt", file1.getString("name"))
        assertFalse(file1.getBoolean("isDirectory"))

        val file2 = jsonArray.getJSONObject(1)
        assertEquals("file2.txt", file2.getString("name"))
        assertFalse(file2.getBoolean("isDirectory"))
    }

    @Test
    fun testHandleFilesNotFound() {
        val resp = sendRequest("GET", "/api/files/nonexistent.txt")
        assertEquals(404, resp.status)
        assertEquals("File Not Found", resp.body)
    }

    @Test
    fun testHandleSoftwareSearchValidation() {
        // 1. Empty query
        val resp = sendRequest("GET", "/api/software/search", "q=")
        assertEquals(400, resp.status)
        assertEquals("[]", resp.body)

        // 2. Spaces in query (URL-encoded as %20)
        val resp2 = sendRequest("GET", "/api/software/search", "q=git%20package")
        assertEquals(400, resp2.status)
        assertEquals("[]", resp2.body)

        // 3. Special characters (Command Injection, URL-encoded)
        val resp3 = sendRequest("GET", "/api/software/search", "q=git%3Brm%20-rf%20%2F")
        assertEquals(400, resp3.status)
        assertEquals("[]", resp3.body)
    }

    @Test
    fun testHandleSoftwareSearchValidQuery() {
        every { prootManager.executeCommand("/usr/bin/dpkg-query -W -f='\${Package}|||\${Status}\n'") } returns "git|||install ok installed"
        every { prootManager.executeCommand("/usr/bin/apt-cache search git") } returns "git - fast, scalable, distributed revision control system\ncurl - command line tool for transferring data"

        val resp = sendRequest("GET", "/api/software/search", "q=git")
        assertEquals(200, resp.status)

        val jsonArray = JSONArray(resp.body)
        assertEquals(2, jsonArray.length())

        val gitItem = jsonArray.getJSONObject(0)
        assertEquals("git", gitItem.getString("name"))
        assertEquals("fast, scalable, distributed revision control system", gitItem.getString("version"))
        assertEquals("Installed", gitItem.getString("status"))

        val curlItem = jsonArray.getJSONObject(1)
        assertEquals("curl", curlItem.getString("name"))
        assertEquals("Available", curlItem.getString("status"))
    }

    @Test
    fun testHandleSoftwareActionValidation() {
        // 1. GET request forbidden (405)
        val resp = sendRequest("GET", "/api/software/action", "pkg=git&action=install")
        assertEquals(405, resp.status)

        // 2. Missing pkg param
        val resp2 = sendRequest("POST", "/api/software/action", "action=install")
        assertEquals(400, resp2.status)
        assertTrue(resp2.body.contains("Invalid package"))

        // 3. Invalid pkg name (special characters)
        val resp3 = sendRequest("POST", "/api/software/action", "pkg=git%3Brm&action=install")
        assertEquals(400, resp3.status)
        assertTrue(resp3.body.contains("Invalid package"))

        // 4. Invalid action
        val resp4 = sendRequest("POST", "/api/software/action", "pkg=git&action=fly")
        assertEquals(400, resp4.status)
        assertTrue(resp4.body.contains("Invalid action"))
    }

    @Test
    fun testHandleSoftwareActionSuccess() {
        every { prootManager.executeCommand(any()) } returns "Reading package lists...\nBuilding dependency tree...\nSetting up git..."

        val resp = sendRequest("POST", "/api/software/action", "pkg=git&action=install")
        assertEquals(200, resp.status)

        val json = JSONObject(resp.body)
        assertTrue(json.getBoolean("success"))
        assertTrue(json.getString("log").contains("Setting up git..."))

        verify { prootManager.executeCommand("DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC /usr/bin/apt-get install -y git") }
    }

    @Test
    fun testHandleSoftwareActionFailure() {
        every { prootManager.executeCommand(any()) } returns "E: Unable to locate package git"

        val resp = sendRequest("POST", "/api/software/action", "pkg=git&action=install")
        assertEquals(200, resp.status)

        val json = JSONObject(resp.body)
        assertFalse(json.getBoolean("success")) // should be false since log contains "E: "
    }

    @Test
    fun testHandleRestart() {
        // 1. GET not allowed (405)
        val resp1 = sendRequest("GET", "/api/restart")
        assertEquals(405, resp1.status)

        // 2. POST triggers stop and start
        val resp2 = sendRequest("POST", "/api/restart")
        assertEquals(200, resp2.status)
        assertEquals("""{"status":"restarted"}""", resp2.body)

        verify { prootManager.stop() }
        verify { prootManager.start(any(), any()) }
    }
}
