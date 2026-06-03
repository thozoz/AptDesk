package com.aptdesk.app

import kotlinx.coroutines.flow.MutableStateFlow

object AptDeskState {
    val state = MutableStateFlow<State>(State.Idle)
    val progress = MutableStateFlow(0)

    sealed class State {
        object Idle : State()
        object DownloadingRootfs : State()
        object ExtractingRootfs : State()
        object ExtractingAssets : State()
        object StartingBackend : State()
        data class Running(val ip: String) : State()
        data class Error(val message: String) : State()
    }
}
