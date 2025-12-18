package com.yskim.sliveguardproject.presentation.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WatchHrBus {

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm = _bpm.asStateFlow()

    fun post(v: Int) {
        _bpm.value = v
    }
}