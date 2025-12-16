package com.yskim.sliveguardproject.wear

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

object DeviceBus {
    data class State(
        val deviceName: String? = null,
        val model: String? = null,
        val manufacturer: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    fun updateDeviceName(name: String?) {
        _state.value = _state.value.copy(deviceName = name)
    }

    fun updateFromJson(json: String) {
        runCatching {
            val obj = JSONObject(json)
            _state.value = State(
                deviceName = obj.optString("deviceName", null),
                model = obj.optString("model", null),
                manufacturer = obj.optString("manufacturer", null)
            )
        }
    }
}