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
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.network.video.VideoApiClient
import com.yskim.sliveguardproject.wear.HrvBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max

class DrowsyMonitoringService: Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private data class Sample(val ts: Long, val v: Double)
    private val hrvBuf = ArrayDeque<Sample>(300)

    private var lastVideoTs: Long = 0L

    companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val NOTI_ID = 1001

        private const val POLL_MS = 2000L          // 서버 s_video 갱신 주기에 맞춰 조절
        private const val TOLERANCE_MS = 3000L     // 시간 매칭 허용오차(예: 3초)

        fun start(ctx: Context) {
            val i = Intent(ctx, DrowsyMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                androidx.core.content.ContextCompat.startForegroundService(ctx, i)
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
        startForeground(NOTI_ID, buildNotification("졸음 모니터링 실행 중"))
        startLoops()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLoops() {
//        val token = SessionManager.getAccessToken(this)
//        if (token.isNullOrEmpty()) {
//            stopSelf()
//            Log.d("DROWSY", "token=$token")
//            return
//        }
        val loginId = SessionManager.getLoginId(this)
        if (loginId.isNullOrEmpty()) {
            Log.d("DROWSY", "loginId missing -> stop service")
            stopSelf()
            return
        }

        // 1) s_hrv 계산 및 버퍼링 루프
        scope.launch {
            HrvBus.state.collect { st ->
                val s = st.sHrv ?: return@collect
                val ts = st.sHrvTsMs ?: return@collect
                addHrvSample(ts, s)
            }
        }

        // 2) 서버에서 s_video 지속 조회 (polling)
        scope.launch {
            while (isActive) {
                try {
                    val res = VideoApiClient.api.getScores(afterTs = lastVideoTs)
                    for (it in res.items) {
                        lastVideoTs = maxOf(lastVideoTs, it.ts)
                        onVideoSample(it.ts, it.s_video)
                    }
                } catch (_: Exception) {
                    // 네트워크 실패 시 다음 루프로 (필요하면 backoff)
                }
                delay(POLL_MS)
            }
        }
    }

    private fun addHrvSample(ts: Long, v: Double) {
        synchronized(hrvBuf) {
            if (hrvBuf.size >= 300) hrvBuf.removeFirst()
            hrvBuf.addLast(Sample(ts, v))
        }
    }

    private fun onVideoSample(videoTs: Long, sVideo: Double) {
        val h = nearestHrv(videoTs) ?: return
        if (abs(videoTs - h.ts) > TOLERANCE_MS) return // 너무 시간 차이 크면 패스

        val score = h.v * 0.3 + sVideo * 0.7

        // TODO: 임계값은 너희 기준으로 튜닝
        val state = when {
            score >= 0.8 -> "졸음"
            score >= 0.6 -> "주의"
            else -> "정상"
        }

        // 알림 업데이트
        updateNotification("상태: $state (score=${"%.2f".format(score)})")
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