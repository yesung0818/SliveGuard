package com.yskim.sliveguardproject.wear

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.sqrt


/**
 * IBI(심박 간격) 기반으로 HRV(RMSSD, SDNN)와 S_hrv(0~1)를 계산하는 모듈.
 * - baseline: 처음 약 3분 동안의 IBI로 RMSSD_base, SDNN_base 계산
 * - window: 최근 60초 IBI로 RMSSD_cur, SDNN_cur 계산
 * - baseline 대비 10~30% 변화 구간을 0~1로 정규화해서 S_hrv 출력
 */

object HrvBus {

    data class State(
        val isBaselineReady: Boolean = false,
        val baselineDurationMs: Long = BASELINE_BURATION_MS,
        val baselineRmssdMs: Double? = null,
        val baselineSdnnMs: Double? = null,
        val currentRmssdMs: Double? = null,
        val currentSdnnMs: Double? = null,
        val sHrv: Double? = null, // 0.0 ~ 1.0, 클수록 졸음/피로 쪽
        val sHrvTsMs: Long? = null
    )

    private const val BASELINE_BURATION_MS = 3 * 60 * 1000L // 3분
    private const val WINDOW_DURATION_MS = 60_000L //최근 60초
    private const val DEV_LOW = 0.1  // 10% 변화 이하 = 거의 정상
    private const val DEV_HIGH = 0.3 // 30% 변화 이상 = 의미 이는 변화

    private var lastIbiMs: Int? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val baselineIbis = mutableListOf<Int>()
    private var baselineTotalMs: Long = 0L
    private var baselineReady: Boolean = false
    private var rmssdBaseMs: Double? = null
    private var sdnnBaseMs: Double? = null

    private val windowIbis = ArrayDeque<Int>()
    private var windowTotalMs: Long = 0L

    /** 새 운전 세션 시작 시 baseline/윈도우 리셋용 (원하면 운전 시작 버튼에서 호출) */
    fun reset() {
        baselineIbis.clear()
        baselineTotalMs = 0L
        baselineReady = false
        rmssdBaseMs = null
        sdnnBaseMs = null

        windowIbis.clear()
        windowTotalMs = 0L

        _state.value = State()
    }

    /** WearListenerService → VitalsBus → 여기로 IBI 배치 전달 */
    fun onIbiBatch(ts0Ms: Long, ibis: IntArray) {
        if (ibis.isEmpty()) return
        ibis.forEach { ibiMs ->
            processOneIbi(ibiMs)
        }
    }

    private fun processOneIbi(ibiMs: Int) {
        val intervalMs = ibiMs.coerceAtLeast(0)
        val prev = lastIbiMs
        lastIbiMs = intervalMs

        if (intervalMs !in 400..1500) {
            Log.d("HRV_BASE", "skip IBI (out of range) = $intervalMs")
            return
        }
        if (prev != null && kotlin.math.abs(intervalMs - prev) > 400) {
//            Log.d("HRV_BASE", "skip IBI (sudden jump) = $intervalMs (prev=$prev)")
            return
        }

        val intervalL = intervalMs.toLong()



        // 1) baseline 수집 (처음 3분)
        if (!baselineReady) {
//            Log.d("HRV_BASE", "baseline add IBI = $intervalL")
            baselineIbis.add(intervalMs)
            baselineTotalMs += intervalL
            if (baselineTotalMs >= BASELINE_BURATION_MS && baselineIbis.size >= 5) {
                val baseRmssd = computeRmssd(baselineIbis)
                val baseSdnn  = computeSdnn(baselineIbis)
                rmssdBaseMs = baseRmssd
                sdnnBaseMs  = baseSdnn
                baselineReady = true

                _state.update { prev ->
                    prev.copy(
                        isBaselineReady = true,
                        baselineRmssdMs = baseRmssd,
                        baselineSdnnMs = baseSdnn,
                    )
                }
            }
        }

        // 2) 최근 60초 윈도우 관리
        windowIbis.addLast(intervalMs)
        windowTotalMs += intervalL
        // 60초 초과하면 앞에서부터 제거
        while (windowTotalMs > WINDOW_DURATION_MS && windowIbis.isNotEmpty()) {
            val removed = windowIbis.removeFirst()
            windowTotalMs -= removed.toLong()
        }

        // 3) baseline 준비 안 됐으면 여기서 끝
        val baseR = rmssdBaseMs ?: return
        val baseS = sdnnBaseMs  ?: return
        if (!baselineReady) return
        if (windowIbis.size < 5) return

        // 4) 현재 윈도우 HRV 계산
        val curRmssd = computeRmssd(windowIbis)
        val curSdnn  = computeSdnn(windowIbis)

        // 5) baseline 대비 변화율
        val rRmssd = curRmssd / baseR
        val rSdnn  = curSdnn  / baseS

        val dRmssd = abs(rRmssd - 1.0)
        val dSdnn  = abs(rSdnn  - 1.0)

        val now = System.currentTimeMillis()

        fun normalizeDev(d: Double): Double {
            val raw = (d - DEV_LOW) / (DEV_HIGH - DEV_LOW)
            return raw.coerceIn(0.0, 1.0)
        }

        val sRmssd = normalizeDev(dRmssd)
        val sSdnn  = normalizeDev(dSdnn)
        val sHrv   = 0.5 * sRmssd + 0.5 * sSdnn

        Log.d("HRV",
            "baseRmssd=$baseR curRmssd=$curRmssd " +
                    "baseSdnn=$baseS curSdnn=$curSdnn " +
                    "dRmssd=$dRmssd dSdnn=$dSdnn " +
                    "sRmssd=$sRmssd sSdnn=$sSdnn sHrv=$sHrv"
        )

        _state.update { prev ->
            prev.copy(
                isBaselineReady = true,
                baselineRmssdMs = baseR,
                baselineSdnnMs = baseS,
                currentRmssdMs = curRmssd,
                currentSdnnMs = curSdnn,
                sHrv = sHrv,
                sHrvTsMs = now
            )
        }
    }

    private fun computeRmssd(ibis: List<Int>): Double {
        if (ibis.size < 2) return 0.0
        var sumSq = 0.0
        for (i in 0 until ibis.size - 1) {
            val diff = (ibis[i + 1] - ibis[i].toDouble())
            sumSq += diff * diff
        }
        return sqrt(sumSq / (ibis.size - 1))
    }

    private fun computeSdnn(ibis: List<Int>): Double {
        if (ibis.isEmpty()) return 0.0
        val mean = ibis.average()
        var sumSq = 0.0
        for (v in ibis) {
            val d = v - mean
            sumSq += d * d
        }
        return sqrt(sumSq / ibis.size)
    }
}