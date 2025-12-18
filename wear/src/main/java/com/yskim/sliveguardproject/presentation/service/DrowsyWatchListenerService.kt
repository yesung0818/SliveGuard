package com.yskim.sliveguardproject.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.common.api.Scope
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.presentation.state.WatchDrowsyStateStore

class DrowsyWatchListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d("WATCH_RX", "path=${event.path} size=${event.data.size}")
        Log.d("WATCH_RX", "path=${event.path} payload=${event.data.toString(Charsets.UTF_8)}")
        if (event.path != "/drowsy_state") return

        val text = event.data.toString(Charsets.UTF_8)
        Log.d("WATCH_RX", "payload=$text")
        val parts = text.split("|")
        val state = parts.getOrNull(0) ?: return
        val score = parts.getOrNull(1) ?: "?"
         val ts = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()

        if (state != "졸음") return

        WatchDrowsyStateStore.post(this, state, score, ts)

//        showNotification("state", score)
        showNotification("졸음", score)

        vibrate(state)
    }

    private fun showNotification(state: String, scope: String) {
        val channelId = "drowsy_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "졸음 감지",
                        NotificationManager.IMPORTANCE_HIGH
                    )
                )
            }
        }

        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("졸음 상태: $state")
            .setContentText("score: $scope")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        nm.notify(1001, n)
    }

    private fun vibrate(state: String) {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        val pattern = when (state) {
            "졸음" -> longArrayOf(0, 400, 150, 400, 150, 700)
            "주의" -> longArrayOf(0, 250, 150, 250)
            else -> longArrayOf(0, 80)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }
}