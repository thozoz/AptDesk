package com.aptdesk.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AptDeskStateTest {

    @Before
    fun setUp() {
        AptDeskState.reset()
    }

    @Test
    fun testStateFlowUpdates() {
        AptDeskState.state.value = AptDeskState.State.Idle
        assertEquals(AptDeskState.State.Idle, AptDeskState.state.value)

        AptDeskState.state.value = AptDeskState.State.DownloadingRootfs
        assertEquals(AptDeskState.State.DownloadingRootfs, AptDeskState.state.value)

        AptDeskState.state.value = AptDeskState.State.ExtractingRootfs
        assertEquals(AptDeskState.State.ExtractingRootfs, AptDeskState.state.value)

        AptDeskState.state.value = AptDeskState.State.StartingBackend
        assertEquals(AptDeskState.State.StartingBackend, AptDeskState.state.value)

        val runningState = AptDeskState.State.Running("192.168.1.100")
        AptDeskState.state.value = runningState
        assertEquals(runningState, AptDeskState.state.value)

        val errorState = AptDeskState.State.Error("An error occurred")
        AptDeskState.state.value = errorState
        assertEquals(errorState, AptDeskState.state.value)
    }

    @Test
    fun testProgressUpdates() {
        AptDeskState.progress.value = 0
        assertEquals(0, AptDeskState.progress.value)

        AptDeskState.progress.value = 50
        assertEquals(50, AptDeskState.progress.value)

        AptDeskState.progress.value = 100
        assertEquals(100, AptDeskState.progress.value)
    }

    @Test
    fun testThreadSafetyConcurrentUpdates() = runTest {
        val jobs = List(100) { index ->
            launch(Dispatchers.Default) {
                AptDeskState.progress.value = index
                AptDeskState.state.value = AptDeskState.State.Error("Err $index")
            }
        }
        jobs.forEach { it.join() }
    }
}
