package com.aptdesk.app

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class ProotManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var nativeDir: File
    private lateinit var rootfsDir: File
    private lateinit var libsDir: File
    private lateinit var prootManager: ProotManager

    private var processBuilderFactory: (List<String>) -> ProcessBuilder = { ProcessBuilder(it) }

    @Before
    fun setUp() {
        AptDeskState.reset()
        filesDir = tempFolder.newFolder("files")
        cacheDir = tempFolder.newFolder("cache")
        nativeDir = tempFolder.newFolder("native")
        rootfsDir = File(filesDir, "rootfs")
        libsDir = File(filesDir, "libs")

        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns cacheDir

        val appInfo = mockk<ApplicationInfo>()
        appInfo.nativeLibraryDir = nativeDir.absolutePath
        every { context.applicationInfo } returns appInfo

        // Create standard binaries so validation passes
        File(nativeDir, "libproot.so").apply {
            createNewFile()
            setExecutable(true)
        }
        File(nativeDir, "libproot-loader.so").apply {
            createNewFile()
            setExecutable(true)
        }
        File(nativeDir, "liblibtalloc2.so").apply {
            writeText("talloc binary data")
        }

        processBuilderFactory = { ProcessBuilder(it) }
        prootManager = ProotManager(context) { processBuilderFactory(it) }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun setupMockProcessBuilder(
        mockProcess: Process,
        capturedCommands: MutableList<List<String>>? = null,
        capturedEnv: MutableMap<String, String>? = null
    ) {
        val mockBuilder = mockk<ProcessBuilder>(relaxed = true)
        val mockEnv = mutableMapOf<String, String>()
        every { mockBuilder.environment() } returns mockEnv
        every { mockBuilder.redirectErrorStream(any<Boolean>()) } returns mockBuilder
        every { mockBuilder.start() } answers {
            capturedEnv?.putAll(mockEnv)
            mockProcess
        }
        processBuilderFactory = { command ->
            capturedCommands?.add(command)
            mockBuilder
        }
    }

    @Test
    fun testExecuteCommandMissingRootfs() {
        // rootfsDir is not created, so it should return the error string
        val result = prootManager.executeCommand("ls")
        assertEquals("Error: Rootfs missing", result)
    }

    @Test
    fun testEnsureTallocLibCopiesAndSkips() {
        rootfsDir.mkdirs()

        // Mock ProcessBuilder to succeed
        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "output".byteInputStream()
        every { mockProcess.waitFor(any(), any()) } returns true
        every { mockProcess.isAlive } returns false
        
        setupMockProcessBuilder(mockProcess)

        // 1. Initial run: libtalloc.so.2 should be copied
        val result = prootManager.executeCommand("ls")
        assertEquals("output", result)

        val destinationTalloc = File(libsDir, "libtalloc.so.2")
        assertTrue(destinationTalloc.exists())
        assertEquals("talloc binary data", destinationTalloc.readText())

        // 2. Overwrite target with custom text, verify it is NOT overwritten (skipped)
        destinationTalloc.writeText("already staged custom data")
        prootManager.executeCommand("ls")
        assertEquals("already staged custom data", destinationTalloc.readText())
    }

    @Test
    fun testExecuteCommandConstructsCorrectCommand() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "success".byteInputStream()
        every { mockProcess.waitFor(any(), any()) } returns true
        every { mockProcess.isAlive } returns false

        val capturedCommands = mutableListOf<List<String>>()
        val capturedEnv = mutableMapOf<String, String>()
        setupMockProcessBuilder(mockProcess, capturedCommands, capturedEnv)

        val testCmd = "echo hello"
        prootManager.executeCommand(testCmd)

        assertEquals(1, capturedCommands.size)
        val command = capturedCommands[0]

        // Assert proot components
        assertEquals(File(nativeDir, "libproot.so").absolutePath, command[0])
        assertTrue(command.contains("-0"))
        assertTrue(command.contains("--link2symlink"))
        assertTrue(command.contains("--rootfs=${rootfsDir.absolutePath}"))
        
        // Assert command execution via bash
        val bashIndex = command.indexOf("/bin/bash")
        assertTrue(bashIndex != -1)
        assertEquals("-c", command[bashIndex + 1])
        assertEquals(testCmd, command[bashIndex + 2])

        // Assert Environment Variables
        assertEquals(cacheDir.path, capturedEnv["PROOT_TMP_DIR"])
        assertEquals(File(nativeDir, "libproot-loader.so").absolutePath, capturedEnv["PROOT_LOADER"])
    }

    @Test
    fun testLdLibraryPathPrependEmpty() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor(any(), any()) } returns true
        every { mockProcess.isAlive } returns false

        val capturedEnv = mutableMapOf<String, String>()
        setupMockProcessBuilder(mockProcess, capturedEnv = capturedEnv)

        prootManager.executeCommand("ls")
        assertEquals(libsDir.absolutePath, capturedEnv["LD_LIBRARY_PATH"])
    }

    @Test
    fun testLdLibraryPathPrependExisting() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor(any(), any()) } returns true
        every { mockProcess.isAlive } returns false

        // Pre-populate environment with LD_LIBRARY_PATH
        val mockBuilder = mockk<ProcessBuilder>(relaxed = true)
        val mockEnv = mutableMapOf("LD_LIBRARY_PATH" to "/usr/lib")
        every { mockBuilder.environment() } returns mockEnv
        every { mockBuilder.redirectErrorStream(any<Boolean>()) } returns mockBuilder
        val capturedEnv = mutableMapOf<String, String>()
        every { mockBuilder.start() } answers {
            capturedEnv.putAll(mockEnv)
            mockProcess
        }
        processBuilderFactory = { mockBuilder }

        prootManager.executeCommand("ls")
        assertEquals("${libsDir.absolutePath}:/usr/lib", capturedEnv["LD_LIBRARY_PATH"])
    }

    @Test
    fun testCommandTimeoutDestroysProcess() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "incomplete output".byteInputStream()
        // Simulate timeout (waitFor returns false)
        every { mockProcess.waitFor(120, TimeUnit.SECONDS) } returns false
        every { mockProcess.isAlive } returns true

        setupMockProcessBuilder(mockProcess)

        val result = prootManager.executeCommand("sleep 200")
        assertEquals("Error: Command timed out after 120 seconds.", result)
        verify { mockProcess.destroyForcibly() }
    }

    @Test
    fun testCommandInjectionPassedStraight() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.inputStream } returns "".byteInputStream()
        every { mockProcess.waitFor(any(), any()) } returns true
        every { mockProcess.isAlive } returns false

        val capturedCommands = mutableListOf<List<String>>()
        setupMockProcessBuilder(mockProcess, capturedCommands = capturedCommands)

        val injectionCmd = "git; rm -rf /"
        prootManager.executeCommand(injectionCmd)

        val command = capturedCommands[0]
        val bashIndex = command.indexOf("/bin/bash")
        assertEquals(injectionCmd, command[bashIndex + 2])
    }

    @Test
    fun testStartInterpolatesResolution() {
        rootfsDir.mkdirs()

        val mockProcess = mockk<Process>(relaxed = true)
        every { mockProcess.isAlive } returns true

        val capturedCommands = mutableListOf<List<String>>()
        setupMockProcessBuilder(mockProcess, capturedCommands = capturedCommands)

        prootManager.start("1024x768")

        val command = capturedCommands[0]
        val bashIndex = command.indexOf("/bin/bash")
        val startupScript = command[bashIndex + 2]

        // Verify script is interpolated with 1024x768
        assertTrue(startupScript.contains("Xvfb :0 -screen 0 1024x768x24 -ac"))
    }

    @Test
    fun testVerifyHealthyReturnsFalseWhenNotRunning() {
        // start() was never called, so process is null / not alive.
        val capturedCommands = mutableListOf<List<String>>()
        val mockProcess = mockk<Process>(relaxed = true)
        setupMockProcessBuilder(mockProcess, capturedCommands = capturedCommands)

        val result = prootManager.verifyHealthy()

        assertEquals(false, result)
        // Must not invoke executeCommand (no pidof subprocess spawned) when not running.
        assertTrue(capturedCommands.isEmpty())
    }

    @Test
    fun testVerifyHealthyReturnsTrueWhenAllServicesAlive() {
        rootfsDir.mkdirs()

        val startProcess = mockk<Process>(relaxed = true)
        every { startProcess.isAlive } returns true

        val capturedCommands = mutableListOf<List<String>>()
        val startBuilder = mockk<ProcessBuilder>(relaxed = true)
        every { startBuilder.environment() } returns mutableMapOf()
        every { startBuilder.redirectErrorStream(any<Boolean>()) } returns startBuilder
        every { startBuilder.start() } returns startProcess

        // Each pidof call must get its own Process with a fresh, unread inputStream —
        // a single shared mock's ByteArrayInputStream would be exhausted after the
        // first executeCommand() readText() call.
        val pidofBuilder = mockk<ProcessBuilder>(relaxed = true)
        every { pidofBuilder.environment() } returns mutableMapOf()
        every { pidofBuilder.redirectErrorStream(any<Boolean>()) } returns pidofBuilder
        every { pidofBuilder.start() } answers {
            val proc = mockk<Process>(relaxed = true)
            every { proc.inputStream } returns "1234".byteInputStream()
            every { proc.waitFor(any(), any()) } returns true
            every { proc.isAlive } returns false
            proc
        }

        processBuilderFactory = { command ->
            capturedCommands.add(command)
            if (command.lastOrNull()?.contains("pidof") == true) pidofBuilder else startBuilder
        }
        prootManager = ProotManager(context) { processBuilderFactory(it) }
        prootManager.start("1280x720")

        val result = prootManager.verifyHealthy()

        assertEquals(true, result)
        // 3 pidof calls, one per critical service.
        val pidofCommands = capturedCommands.filter { cmd -> cmd.lastOrNull()?.contains("pidof") == true }
        assertEquals(3, pidofCommands.size)
    }

    @Test
    fun testVerifyHealthyReturnsFalseWhenOneServiceMissing() {
        rootfsDir.mkdirs()

        val startProcess = mockk<Process>(relaxed = true)
        every { startProcess.isAlive } returns true

        val startBuilder = mockk<ProcessBuilder>(relaxed = true)
        every { startBuilder.environment() } returns mutableMapOf()
        every { startBuilder.redirectErrorStream(any<Boolean>()) } returns startBuilder
        every { startBuilder.start() } returns startProcess

        // Each pidof call gets its own process: Xvfb and x0vncserver succeed, caddy returns empty.
        val pidofBuilder = mockk<ProcessBuilder>(relaxed = true)
        every { pidofBuilder.environment() } returns mutableMapOf()
        every { pidofBuilder.redirectErrorStream(any<Boolean>()) } returns pidofBuilder
        every { pidofBuilder.start() } answers {
            val proc = mockk<Process>(relaxed = true)
            every { proc.waitFor(any(), any()) } returns true
            every { proc.isAlive } returns false
            proc
        }

        val capturedCommands = mutableListOf<List<String>>()
        processBuilderFactory = { command ->
            capturedCommands.add(command)
            if (command.lastOrNull()?.contains("pidof") == true) pidofBuilder else startBuilder
        }
        prootManager = ProotManager(context) { processBuilderFactory(it) }
        prootManager.start("1280x720")

        // Stub inputStream per call order: Xvfb -> pid, x0vncserver -> pid, caddy -> empty.
        var callIndex = 0
        val outputs = listOf("1234", "5678", "")
        every { pidofBuilder.start() } answers {
            val output = outputs.getOrElse(callIndex) { "" }
            callIndex++
            val proc = mockk<Process>(relaxed = true)
            every { proc.inputStream } returns output.byteInputStream()
            every { proc.waitFor(any(), any()) } returns true
            every { proc.isAlive } returns false
            proc
        }

        val result = prootManager.verifyHealthy()

        assertEquals(false, result)
    }
}
