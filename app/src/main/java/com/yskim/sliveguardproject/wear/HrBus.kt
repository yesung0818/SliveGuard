package com.yskim.sliveguardproject.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HrBus {
    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()
    fun post(value: Int) {
        _bpm.value = value
    }
}