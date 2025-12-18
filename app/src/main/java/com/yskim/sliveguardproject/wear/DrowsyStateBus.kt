package com.yskim.sliveguardproject.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object DrowsyStateBus {

    data class State(
        val stage: String,
        val score: Double,
        val ts: Long
    )

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    fun post(stage: String, score: Double, ts: Long) {
        _state.value = State(stage, score, ts)
    }

}