package com.yskim.sliveguardproject.presentation.state

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object WatchDrowsyStateStore {

    data class State(val stage: String, val score: String, val ts: Long)

    private const val PREF = "drowsy_pref"
    private const val K_STAGE = "stage"
    private const val K_SCORE = "score"
    private const val K_TS = "ts"

    private val _state = MutableStateFlow<State?>(null)
    val state = _state.asStateFlow()

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val stage = sp.getString(K_STAGE, null) ?: return
        val score = sp.getString(K_SCORE, "?") ?: "?"
        val ts = sp.getLong(K_TS, 0L)
        _state.value = State(stage, score, ts)
    }

    fun post(ctx: Context, stage: String, score: String, ts: Long) {
        _state.value = State(stage, score, ts)

        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(K_STAGE, stage)
            .putString(K_SCORE, score)
            .putLong(K_TS, ts)
            .apply()
    }
}