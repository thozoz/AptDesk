package com.aptdesk.app

import android.util.Log

/**
 * Plain Kotlin class extracted from [MainService] so the stop/reset
 * error-isolation behavior is unit-testable without Robolectric.
 *
 * Each cleanup step is wrapped in its own try/catch so a failure in one
 * resource's shutdown never prevents the others from being attempted
 * (mirrors [ProotManager]'s per-command isolation in killOrphanedProcesses()).
 */
class MainServiceLogic {

    /**
     * Stops the backend, isolating failures per-resource:
     * - [cancelJob] (cancel the running coroutine job)
     * - [stopProot] (stop the PRoot subprocess manager)
     * - [stopWebServer] (stop the embedded HTTP server)
     *
     * A failure in any one step is logged and does not block the others.
     * Always finishes by setting [AptDeskState.state] to [AptDeskState.State.Idle].
     */
    fun stopBackendLogic(
        cancelJob: () -> Unit,
        stopProot: () -> Unit,
        stopWebServer: () -> Unit
    ) {
        try {
            cancelJob()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling running job", e)
        }

        try {
            stopProot()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proot", e)
        }

        try {
            stopWebServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping web server", e)
        }

        AptDeskState.state.value = AptDeskState.State.Idle
    }

    /**
     * Resets backend services via [resetServices], isolating any failure so
     * it never propagates out of the caller.
     */
    fun resetLogic(resetServices: () -> Unit) {
        try {
            resetServices()
        } catch (e: Exception) {
            Log.e(TAG, "Reset failed", e)
        }
    }

    companion object {
        private const val TAG = "MainServiceLogic"
    }
}
