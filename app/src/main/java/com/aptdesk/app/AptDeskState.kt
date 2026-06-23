package com.aptdesk.app

import kotlinx.coroutines.flow.MutableStateFlow

object AptDeskState {
    val state = MutableStateFlow<State>(State.Idle)
    val progress = MutableStateFlow(0)

    fun reset() {
        state.value = State.Idle
        progress.value = 0
    }

    sealed class State {
        abstract val stateName: String

        object Idle : State() { override val stateName = "idle" }
        object DownloadingRootfs : State() { override val stateName = "downloading_rootfs" }
        object ExtractingRootfs : State() { override val stateName = "extracting_rootfs" }
        object ExtractingAssets : State() { override val stateName = "copying_assets" }
        object StartingBackend : State() { override val stateName = "starting_backend" }
        data class Running(val ip: String) : State() { override val stateName = "running" }
        data class Error(val message: String) : State() { override val stateName = "error" }
    }
}
