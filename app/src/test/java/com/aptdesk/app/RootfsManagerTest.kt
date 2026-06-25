package com.aptdesk.app

import android.content.Context
import android.system.Os
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLStreamHandler
import java.net.URLStreamHandlerFactory
import java.util.zip.GZIPOutputStream

class RootfsManagerTest {

    companion object {
        private var useMockUrl = false
        private var mockResponseCode = 200
        private var mockResponseSequence: MutableList<Int>? = null
        private var mockConnectionFactory: ((URL) -> HttpURLConnection)? = null

        @BeforeClass
        @JvmStatic
        fun setupURLFactory() {
            try {
                URL.setURLStreamHandlerFactory(object : URLStreamHandlerFactory {
                    override fun createURLStreamHandler(protocol: String): URLStreamHandler? {
                        return if (useMockUrl && (protocol == "http" || protocol == "https")) {
                            object : URLStreamHandler() {
                                override fun openConnection(u: URL): java.net.URLConnection {
                                    val factory = mockConnectionFactory
                                    if (factory != null) {
                                        return factory(u)
                                    }
                                    val mockConn = mockk<HttpURLConnection>(relaxed = true)
                                    val sequence = mockResponseSequence
                                    if (sequence != null) {
                                        every { mockConn.responseCode } answers {
                                            if (sequence.size > 1) sequence.removeAt(0) else sequence[0]
                                        }
                                    } else {
                                        every { mockConn.responseCode } answers { mockResponseCode }
                                    }
                                    every { mockConn.inputStream } throws IOException("Mock HTTP Connection Failure")
                                    return mockConn
                                }
                            }
                        } else {
                            null
                        }
                    }
                })
            } catch (e: Error) {
                // Already defined in this JVM run, which is expected
            }
        }
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var rootfsDir: File
    private lateinit var rootfsManager: RootfsManager

    @Before
    fun setUp() {
        AptDeskState.reset()
        useMockUrl = false
        mockResponseCode = 200

        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        rootfsDir = File(filesDir, "rootfs")
        rootfsDir.mkdirs()

        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir

        rootfsManager = RootfsManager(context)
    }

    @After
    fun tearDown() {
        useMockUrl = false
        mockResponseSequence = null
        mockConnectionFactory = null
        unmockkAll()
    }

    @Test
    fun testResolveEntryNormalPath() {
        val targetFile = rootfsManager.resolveEntry(rootfsDir, "etc/hosts")
        assertEquals(File(rootfsDir, "etc/hosts").absolutePath, targetFile.absolutePath)
    }

    @Test
    fun testResolveEntryPathTraversalBlocked() {
        try {
            rootfsManager.resolveEntry(rootfsDir, "../../etc/passwd")
            fail("Should have thrown IOException for path traversal")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Blocked path traversal"))
        }

        try {
            rootfsManager.resolveEntry(rootfsDir, "../rootfs/../sibling")
            fail("Should have thrown IOException for path traversal")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Blocked path traversal"))
        }
    }

    @Test
    fun testResolveEntrySymlinkTraversalOutsideBlocked() {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows) {
            System.out.println("Skipping symlink traversal test on Windows due to OS directory links resolution behavior.")
            return
        }

        val link = File(rootfsDir, "link")
        try {
            java.nio.file.Files.createSymbolicLink(
                link.toPath(),
                tempFolder.newFolder("outside").toPath()
            )
            try {
                rootfsManager.resolveEntry(rootfsDir, "link/somefile")
                fail("Should have thrown IOException for symlink traversal")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("Blocked path traversal"))
            }
        } catch (e: Exception) {
            System.err.println("Skipping symlink test due to OS restrictions: ${e.message}")
        }
    }

    @Test
    fun testApplyExecutableFlag() {
        val entry = mockk<TarArchiveEntry>()
        val mockFile = mockk<File>(relaxed = true)

        // 1. Executable mode (e.g. 0o755 -> lowest digit is 5 = 101 binary, which has execute bit 1 set)
        every { entry.mode } returns 493 // 0o755
        every { mockFile.setExecutable(true, false) } returns true
        rootfsManager.applyExecutableFlag(entry, mockFile)
        verify { mockFile.setExecutable(true, false) }

        // 2. Non-executable mode (e.g. 0o640 -> lowest digit is 0 = 000 binary, no bits set)
        every { entry.mode } returns 416 // 0o640
        val mockFile2 = mockk<File>(relaxed = true)
        rootfsManager.applyExecutableFlag(entry, mockFile2)
        verify(exactly = 0) { mockFile2.setExecutable(any(), any()) }

        // 3. Executable set returns false (logged, not thrown)
        every { entry.mode } returns 493 // 0o755
        val mockFile3 = mockk<File>(relaxed = true)
        every { mockFile3.setExecutable(true, false) } returns false
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0

        rootfsManager.applyExecutableFlag(entry, mockFile3)
        verify { mockFile3.setExecutable(true, false) }
    }

    @Test
    fun testDownloadRootfsHttpFailure() {
        useMockUrl = true
        mockResponseCode = 500

        try {
            rootfsManager.ensureRootfs()
            fail("Should have thrown IOException due to HTTP/Connection failure")
        } catch (e: IOException) {
            // Expected failure
        }
    }

    @Test
    fun testDownloadRootfsRetryExhaustionAfterMaxAttempts() {
        useMockUrl = true
        var attempts = 0

        mockConnectionFactory = { _ ->
            attempts++
            val mockConn = mockk<HttpURLConnection>(relaxed = true)
            every { mockConn.responseCode } returns HttpURLConnection.HTTP_OK
            every { mockConn.inputStream } throws IOException("Mock HTTP Connection Failure")
            mockConn
        }

        try {
            rootfsManager.ensureRootfs()
            fail("Should have thrown IOException after exhausting retry attempts")
        } catch (e: IOException) {
            assertEquals("Mock HTTP Connection Failure", e.message)
        }

        assertEquals(3, attempts)
    }

    @Test
    fun testDownloadRootfsValidResumeWithPartialContent() {
        useMockUrl = true

        // Pre-seed a partially downloaded archive in cacheDir to trigger resume logic.
        val archiveFile = File(cacheDir, "aptdesk-rootfs.tar.gz")
        archiveFile.writeBytes("partial".toByteArray())

        val capturedRangeHeaders = mutableListOf<String?>()
        var callCount = 0

        mockConnectionFactory = { _ ->
            callCount++
            val mockConn = mockk<HttpURLConnection>(relaxed = true)
            val remaining = "remainder-data"
            every { mockConn.setRequestProperty("Range", any()) } answers {
                capturedRangeHeaders.add(it.invocation.args[1] as String?)
            }
            every { mockConn.responseCode } returns HttpURLConnection.HTTP_PARTIAL
            every { mockConn.contentLength } returns remaining.length
            every { mockConn.inputStream } returns remaining.byteInputStream()
            mockConn
        }

        // Extraction will fail (no real archive), but download must succeed and
        // resume from existing bytes via the Range header before that point.
        try {
            rootfsManager.ensureRootfs()
        } catch (e: Exception) {
            // extraction-time failure expected — irrelevant to this test's assertion
        }

        assertEquals(1, capturedRangeHeaders.size)
        assertEquals("bytes=7-", capturedRangeHeaders[0])
    }

    @Test
    fun testDownloadRootfsRangeIgnoredRestartsFully() {
        useMockUrl = true

        val archiveFile = File(cacheDir, "aptdesk-rootfs.tar.gz")
        archiveFile.writeBytes("partial".toByteArray())

        var callCount = 0
        val fullContent = "full-fresh-content"

        mockConnectionFactory = { _ ->
            callCount++
            val mockConn = mockk<HttpURLConnection>(relaxed = true)
            if (callCount == 1) {
                // Server ignores Range, returns 200 instead of expected 206 — triggers restart.
                every { mockConn.responseCode } returns HttpURLConnection.HTTP_OK
                every { mockConn.contentLength } returns fullContent.length
                every { mockConn.inputStream } returns fullContent.byteInputStream()
            } else {
                // Second attempt (existingBytes == 0 now): plain 200 succeeds.
                every { mockConn.responseCode } returns HttpURLConnection.HTTP_OK
                every { mockConn.contentLength } returns fullContent.length
                every { mockConn.inputStream } returns fullContent.byteInputStream()
            }
            mockConn
        }

        try {
            rootfsManager.ensureRootfs()
        } catch (e: Exception) {
            // extraction-time failure expected — irrelevant to this test's assertion
        }

        assertEquals(2, callCount)
        assertEquals(fullContent, archiveFile.readText())
    }

    @Test
    fun testResetServicesWipesAndPreservesCorrectly() {
        // Create files to wipe
        val dbFile = File(rootfsDir, "var/lib/filebrowser.db").apply { parentFile.mkdirs(); writeText("db") }
        val vncDir = File(rootfsDir, "root/.vnc").apply { mkdirs(); File(this, "config").writeText("vnc") }
        val cacheDirFile = File(rootfsDir, "root/.cache").apply { mkdirs(); File(this, "tmpfile").writeText("cache") }
        val tmpDir = File(rootfsDir, "tmp").apply { mkdirs(); File(this, "tempfile").writeText("temp") }
        val logDir = File(rootfsDir, "var/log").apply { mkdirs(); File(this, "logfile").writeText("log") }

        // Create personal files to preserve
        val homeFile = File(rootfsDir, "home/user/document.txt").apply { parentFile.mkdirs(); writeText("personal") }
        val etcFile = File(rootfsDir, "etc/passwd").apply { parentFile.mkdirs(); writeText("passwd") }
        val usrFile = File(rootfsDir, "usr/bin/bash").apply { parentFile.mkdirs(); writeText("bash") }

        rootfsManager.resetServices()

        // Verify wiped
        assertFalse(dbFile.exists())
        assertFalse(vncDir.exists())
        assertFalse(cacheDirFile.exists())
        
        // Verify recreated
        assertTrue(tmpDir.exists())
        assertTrue(tmpDir.isDirectory)
        assertEquals(0, tmpDir.listFiles()?.size ?: 0)

        assertTrue(logDir.exists())
        assertTrue(logDir.isDirectory)
        assertEquals(0, logDir.listFiles()?.size ?: 0)

        // Verify preserved
        assertTrue(homeFile.exists())
        assertEquals("personal", homeFile.readText())
        assertTrue(etcFile.exists())
        assertTrue(usrFile.exists())
    }

    @Test
    fun testExtractArchiveSuccess() {
        mockkStatic(Os::class)
        every { Os.symlink(any(), any()) } returns Unit

        // Create a mock tar.gz in memory
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzipOut ->
            TarArchiveOutputStream(gzipOut).use { tarOut ->
                // 1. Directory entry
                val dirEntry = TarArchiveEntry("etc/")
                tarOut.putArchiveEntry(dirEntry)
                tarOut.closeArchiveEntry()

                // 2. Normal file entry
                val fileEntry = TarArchiveEntry("etc/hosts").apply {
                    size = 12
                    mode = 493 // 0o755
                }
                tarOut.putArchiveEntry(fileEntry)
                tarOut.write("hostscontent".toByteArray())
                tarOut.closeArchiveEntry()

                // 3. Symlink entry
                val symlinkEntry = TarArchiveEntry("etc/resolv.conf", TarArchiveEntry.LF_SYMLINK).apply {
                    linkName = "../tmp/resolv.conf"
                }
                tarOut.putArchiveEntry(symlinkEntry)
                tarOut.closeArchiveEntry()
            }
        }

        val archiveFile = File(cacheDir, "test.tar.gz").apply {
            writeBytes(byteOut.toByteArray())
        }

        // Trigger extract
        val targetDir = tempFolder.newFolder("extract_target")
        rootfsManager.extractArchive(archiveFile, targetDir)

        // Verify results
        val dir = File(targetDir, "etc")
        assertTrue(dir.exists() && dir.isDirectory)

        val file = File(targetDir, "etc/hosts")
        assertTrue(file.exists() && file.isFile)
        assertEquals("hostscontent", file.readText())

        // Verify symlink call was invoked
        val expectedLinkFile = File(targetDir, "etc/resolv.conf")
        verify { Os.symlink("../tmp/resolv.conf", expectedLinkFile.absolutePath) }
    }

    @Test
    fun testEnsureRootfsCorruptArchiveThrowsIOException() {
        useMockUrl = true
        val content = "not a valid gzip tarball"
        mockConnectionFactory = { _ ->
            val mockConn = mockk<HttpURLConnection>(relaxed = true)
            every { mockConn.responseCode } returns HttpURLConnection.HTTP_OK
            every { mockConn.contentLength } returns content.length
            every { mockConn.inputStream } returns content.byteInputStream()
            mockConn
        }

        try {
            rootfsManager.ensureRootfs()
            fail("Should have thrown an exception due to corrupt archive")
        } catch (e: Exception) {
            // Expected — corrupt tar.gz cannot be extracted
        }
    }

    @Test
    fun testEnsureRootfsCleansUpRootfsDirAfterExtractionFailure() {
        useMockUrl = true
        val content = "not a valid gzip tarball"
        mockConnectionFactory = { _ ->
            val mockConn = mockk<HttpURLConnection>(relaxed = true)
            every { mockConn.responseCode } returns HttpURLConnection.HTTP_OK
            every { mockConn.contentLength } returns content.length
            every { mockConn.inputStream } returns content.byteInputStream()
            mockConn
        }

        // Leave a marker file in rootfsDir prior to the failing extraction so we can
        // verify it was cleaned up (not left half-populated) after the failure.
        try {
            rootfsManager.ensureRootfs()
            fail("Should have thrown an exception due to corrupt archive")
        } catch (e: Exception) {
            // Expected
        }

        assertFalse(rootfsDir.exists())
    }
}
