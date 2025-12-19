package com.yskim.sliveguardproject.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.network.auth.AuthApiClient
import com.yskim.sliveguardproject.network.auth.StartStopMeasurementRequest
import com.yskim.sliveguardproject.network.auth.VideoScorePostRequest
import com.yskim.sliveguardproject.wear.DrowsyStateBus
import com.yskim.sliveguardproject.wear.HrvBus
import com.yskim.sliveguardproject.wear.PhoneWearTx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log
import kotlin.math.max

class DrowsyMonitoringService: Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Sample(val ts: Long, val v: Double)
    private val hrvBuf = ArrayDeque<Sample>(300)

    private var lastVideoTs: Long = 0L

    private var loopsRunning = false

    private var measureJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val NOTI_ID = 1001

        private const val POLL_MS = 1000L          // 서버 s_video 갱신 주기에 맞춰 조절
        private const val TOLERANCE_MS = 3000L     // 시간 매칭 허용오차(예: 3초)

        const val ACTION_START_MEASURE = "ACTION_START_MEASURE"
        const val ACTION_STOP_MEASURE = "ACTION_STOP_MEASURE"

        fun startMeasure(ctx: Context) {
            val i  = Intent(ctx, DrowsyMonitoringService::class.java).apply {
                action = ACTION_START_MEASURE
            }
            ctx.startService(i)
        }

        fun stopMeasure(ctx: Context) {
            val i  = Intent(ctx, DrowsyMonitoringService::class.java).apply {
                action = ACTION_STOP_MEASURE
            }
            ctx.startService(i)
        }

        fun start(ctx: Context) {
            val i = Intent(ctx, DrowsyMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(ctx, i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DrowsyMonitoringService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
//        startForeground(NOTI_ID, buildNotification("졸음 모니터링 실행 중"))
        startForeground(NOTI_ID, buildNotification("대기 중 (워치 시작 버튼을 누르세요)"))
//        startLoops()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("DROWSY", "onStartCommand action=${intent?.action}")

        if (action == null) {
            SessionManager.setMeasuring(this, false)
            updateNotification("대기 중 (워치 시작 버튼을 누르세요)")
            loopsRunning = false
            measureJob?.cancel()
            measureJob = null
            return START_STICKY
        }

        when (action) {
            ACTION_START_MEASURE -> {
                if (!loopsRunning) {
                    if (measureJob?.isActive == true) return START_STICKY

                    SessionManager.setMeasuring(this, true)
                    updateNotification("준비 중 (베이스라인 측정 중, 약 3분)")

                    loopsRunning = true
                    measureJob = scope.launch { startLoops() }
//                    startLoops()
                }
            }
            ACTION_STOP_MEASURE -> {
                stopMeasuringInternal()
//                SessionManager.setMeasuring(this, false)
//                loopsRunning = false
//                measureJob?.cancel()
//                measureJob = null
//
//                val loginId = SessionManager.getLoginId(this)
//                if (!loginId.isNullOrBlank()) {
//                    scope.launch(Dispatchers.IO) { callStopMeasurement(loginId) }
//                }
//                updateNotification("대기 중 (워치 시작 버튼을 누르세요)")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        SessionManager.setMeasuring(this, false)
        loopsRunning = false
        measureJob?.cancel()
        measureJob = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startLoops() = coroutineScope {
        val loginId = SessionManager.getLoginId(this@DrowsyMonitoringService)
        if (loginId.isNullOrEmpty()) {
            Log.d("DROWSY", "loginId missing -> stop service")
            stopSelf()
            return@coroutineScope
        }

//        scope.launch(Dispatchers.IO) {
//            callStartMeasurement(loginId)
//        }
        launch(Dispatchers.IO) {
            try {
                callStartMeasurement(loginId)
            } catch (e: Exception) {
                Log.e("DROWSY", "callStartMeasurement failed", e)
            }
        }

        // 1) s_hrv 계산 및 버퍼링 루프
//        scope.launch {
        launch {
            HrvBus.state.collect { st ->
                val s = st.sHrv ?: return@collect
                val ts = st.sHrvTsMs ?: return@collect
                addHrvSample(ts, s)
            }
        }



        // 2) 서버에서 s_video 지속 조회 (polling) - 문서(최신) 방식
//        scope.launch(Dispatchers.IO) {
        launch(Dispatchers.IO) {
            var lastStartRetryAt = 0L

            //로그용
            var lastBaselineLogAt = 0L

            while (isActive && loopsRunning) {
                val now = System.currentTimeMillis()
                val res = try {
                    AuthApiClient.api.getVideoScore(loginId, now)
                } catch (_: Exception) {
                    AuthApiClient.api.postVideoScore(VideoScorePostRequest(loginId, now))
                }

//                val res = try {
//                    AuthApiClient.api.getVideoScore(loginId, now)
//                } catch (e: HttpException) {
//                    when (e.code()) {
//                        404, 409 -> AuthApiClient.api.postVideoScore(VideoScorePostRequest(loginId, now))
//                        else -> throw e
//                    }
//                } catch (e: Exception) {
//                    throw e
//                }

//                val res = try {
//                    AuthApiClient.api.getVideoScore(loginId, now)
//                } catch (e: Exception) {
//                    Log.e("DROWSY", "video-score GET failed", e)
//                    delay(POLL_MS)
//                    continue@polling
//                }

//                val result = runCatching {
//                    AuthApiClient.api.getVideoScore(loginId, now)
//                }.recoverCatching {
//                    AuthApiClient.api.postVideoScore(VideoScorePostRequest(loginId, now))
//                }
//
//                if (result.isFailure) {
//                    Log.e("DROWSY", "video-score fetch failed (get+post)", result.exceptionOrNull())
//                    delay(POLL_MS)
//                    continue@polling
//                }
//
//                val res = result.getOrThrow()

                val videoTs = res.ts ?: now

                when {
                    res.ok && res.s_video != null -> {
                        onVideoSample(videoTs, res.s_video)
                    }

                    res.ok && res.s_video == null -> {
                        //로그용
                        if (now - lastBaselineLogAt > 5000L) {
                            lastBaselineLogAt = now
                            Log.d("DROWSY", "baseline collecting: ${res.message}")
                        }
                    }

                    !res.ok -> {
                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastStartRetryAt > 5000L) {
                            lastStartRetryAt = nowMs
                            callStartMeasurement(loginId)
                        }
                        Log.w("DROWSY", "video-score not ready: ${res.message}")
                    }

                }
                delay(POLL_MS)
//                if (res.ok) {
//                    val sv = res.s_video
//                    if (sv != null) {
//                        val videoTs = res.ts ?: now
//                        onVideoSample(videoTs, sv)
//                    }
//                } else {
//                    if (now - lastStartRetryAt > 5000L) {
//                        lastStartRetryAt = now
//                        launch(Dispatchers.IO) {
//                            callStartMeasurement(loginId)
//                        }
//                    }
//                }

            }
        }

    }

    private fun stopMeasuringInternal() {
        SessionManager.setMeasuring(this, false)
        loopsRunning = false
        measureJob?.cancel()
        measureJob = null

        val loginId = SessionManager.getLoginId(this)
        if (!loginId.isNullOrBlank()) {
            scope.launch(Dispatchers.IO) {
                try {
                    callStopMeasurement(loginId)
                }catch (e: Exception) {
                    Log.e("DROWSY", "callStopMeasurement failed", e)
                }
            }
        }

        updateNotification("대기 중 (워치 시작 버튼을 누르세요)")
    }

    private fun addHrvSample(ts: Long, v: Double) {
        synchronized(hrvBuf) {
            if (hrvBuf.size >= 300) hrvBuf.removeFirst()
            hrvBuf.addLast(Sample(ts, v))
        }
    }

    private var lastSentState: String? = null
    private var lastSentAt = 0L

    private fun normalizeVideo(sVideo: Double): Double {
        val x = sVideo.coerceIn(0.0, 1.0)
        return when {
            // 0.4 졸음 기준
//            x < 0.4 -> (x / 0.4) * 0.6
//            else -> 0.6 + ((x - 0.4) / 0.6) * 0.4

            // 0.6 졸음 기준
            x < 0.3 -> x / 0.3 * 0.3
            x < 0.6 -> 0.3 + (x - 0.3) / 0.3 * 0.4
            else -> 0.7 + (x - 0.6) / 0.4 * 0.3
        }.coerceIn(0.0, 1.0)
    }

    private fun onVideoSample(videoTs: Long, sVideo: Double) {
        val h = nearestHrv(videoTs) ?: return
        if (abs(videoTs - h.ts) > TOLERANCE_MS) return // 너무 시간 차이 크면 패스

//        val vVideo = normalizeVideo(sVideo)
        val vVideo = sVideo.coerceIn(0.0, 1.0)
        val score = h.v * 0.3 + vVideo * 0.7
//        val score = h.v * 0.3 + sVideo * 0.7

//        val state = when {
//            score >= 0.8 -> "졸음"
//            score >= 0.5 -> "주의"
//            else -> "정상"
//        }

        // 0.4 졸음 기준
//        val state = when {
//            sVideo >= 0.5 || score >= 0.75 -> "졸음"
//            sVideo >= 0.4 || score >= 0.55 -> "주의"
//            else -> "정상"
//        }

        // 0.6 졸음 기준
//        val state = when {
//            sVideo >= 0.6 -> "졸음"
//            sVideo >= 0.3 || score >= 0.55 -> "주의"
//            else -> "정상"
//        }

        //데모 버전
        val state = when {
            sVideo >= 0.50 || score >= 0.65 -> "졸음"
            sVideo >= 0.25 || score >= 0.50 -> "주의"
            else -> "정상"
        }

        DrowsyStateBus.post(
            stage = state,
            score = score,
            ts = videoTs
        )

        Log.d("DROWSY", "측정 결과값 : state=$state score=${"%.2f".format(score)} (s_video=${"%.2f".format(sVideo)} hrv=${"%.2f".format(h.v)})")
        // 알림 업데이트
        updateNotification("상태: $state (score=${"%.2f".format(score)})")

        val now = System.currentTimeMillis()
        val shouldSend = (state != lastSentState) || (now - lastSentAt > 5000L)
        if (shouldSend) {
            lastSentState = state
            lastSentAt = now
            val payload = "$state|${"%.2f".format(score)}|$videoTs"
            PhoneWearTx.sendToWatch(applicationContext, "/drowsy_state", payload.toByteArray())
        }

    }

    private fun nearestHrv(ts: Long): Sample? {
        synchronized(hrvBuf) {
            if (hrvBuf.isEmpty()) return null
            var best = hrvBuf.first()
            var bestDiff = abs(best.ts - ts)
            for (s in hrvBuf) {
                val d = abs(s.ts - ts)
                if (d < bestDiff) { best = s; bestDiff = d }
            }
            return best
        }
    }


    private suspend fun callStartMeasurement(userId: String) {
        try {
            val res = AuthApiClient.api.startMeasurement(StartStopMeasurementRequest(userId))
            Log.d("DROWSY", "startMeasurement ok=${res.ok} msg=${res.message}")
        } catch (e: Exception) {
            Log.e("DROWSY", "startMeasurement failed", e)
        }
    }

    private suspend fun callStopMeasurement(userId: String) {
        try {
            val res = AuthApiClient.api.stopMeasurement(StartStopMeasurementRequest(userId))
            Log.d("DROWSY", "stopMeasurement ok=${res.ok} msg=${res.message}")
        } catch (e: Exception) {
            Log.e("DROWSY", "stopMeasurement failed", e)
        }
    }



    private fun buildNotification(text: String): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setContentTitle("SliveGuard")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTI_ID, buildNotification(text))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Monitoring", NotificationManager.IMPORTANCE_LOW)
        )
    }
}