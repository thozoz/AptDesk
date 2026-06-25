package com.aptdesk.app

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MainServiceLogicTest {

    private lateinit var logic: MainServiceLogic

    @Before
    fun setUp() {
        AptDeskState.reset()
        logic = MainServiceLogic()
    }

    @Test
    fun `stopBackendLogic still calls webServer stop when prootManager stop throws`() {
        var webServerStopCalled = false
        var jobCancelCalled = false

        logic.stopBackendLogic(
            cancelJob = { jobCancelCalled = true },
            stopProot = { throw RuntimeException("proot stop failed") },
            stopWebServer = { webServerStopCalled = true }
        )

        assertEquals(true, jobCancelCalled)
        assertEquals(true, webServerStopCalled)
        assertEquals(AptDeskState.State.Idle, AptDeskState.state.value)
    }

    @Test
    fun `stopBackendLogic ends in Idle state when webServer stop throws and prootManager stop succeeds`() {
        var prootStopCalled = false

        // Should not throw out of this call.
        logic.stopBackendLogic(
            cancelJob = { },
            stopProot = { prootStopCalled = true },
            stopWebServer = { throw RuntimeException("web server stop failed") }
        )

        assertEquals(true, prootStopCalled)
        assertEquals(AptDeskState.State.Idle, AptDeskState.state.value)
    }

    @Test
    fun `resetLogic does not propagate when resetServices throws IOException`() {
        var exceptionEscaped = false

        try {
            logic.resetLogic(resetServices = { throw java.io.IOException("reset failed") })
        } catch (e: Exception) {
            exceptionEscaped = true
        }

        assertEquals(false, exceptionEscaped)
    }

    @Test
    fun `stopBackendLogic is idempotent when called twice in rapid succession`() {
        var cancelCount = 0
        var prootStopCount = 0
        var webServerStopCount = 0

        repeat(2) {
            logic.stopBackendLogic(
                cancelJob = { cancelCount++ },
                stopProot = { prootStopCount++ },
                stopWebServer = { webServerStopCount++ }
            )
        }

        assertEquals(2, cancelCount)
        assertEquals(2, prootStopCount)
        assertEquals(2, webServerStopCount)
        assertEquals(AptDeskState.State.Idle, AptDeskState.state.value)
    }
}
