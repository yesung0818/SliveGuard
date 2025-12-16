package com.yskim.sliveguardproject.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object VitalsBus {
    data class State(
        val hrBpm: Int? = null,
        val lastIbiMs: Int? = null,
        val recentIbiMs: List<Int> = emptyList() // UI에서 최근 5개 표시 용
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun postHr(ts: Long, bpm: Int) {
        _state.update { it.copy(hrBpm = bpm) }
    }

    fun postIbiBatch(ts0: Long, ibis: IntArray) {
        val last = ibis.lastOrNull()
        _state.update { prev ->
            val merged = (prev.recentIbiMs + ibis.toList()).takeLast(5)
            prev.copy(lastIbiMs = last, recentIbiMs = merged)
        }

        HrvBus.onIbiBatch(ts0, ibis)
    }
}