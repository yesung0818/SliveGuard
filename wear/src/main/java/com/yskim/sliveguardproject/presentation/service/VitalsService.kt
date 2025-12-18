package com.yskim.sliveguardproject.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.presentation.state.WatchHrBus
import com.yskim.sliveguardproject.presentation.util.WearTx
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class VitalsService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartRate: Float = 0f
    private var lastBeatNs: Long? = null
    private val ibiBufferMs = mutableListOf<Int>()

    private val sendIntervalMs = 5_000L              // IBI를 5초마다 배치 전송
    private var lastSendMs = 0L

    private var lastValidBpm: Int = -1
    private var lastValidBpmTsMs: Long = 0L
    private val HOLD_MS = 3_000L

    companion object {
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VitalsService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        WearTx.sendDeviceInfo(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        Log.d("Vitals", "HB sensor? = ${sensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT) != null}")
        startForegroundWithNotif()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT)?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onSensorChanged(event: SensorEvent) {
        Log.d("Vitals", "HR event: ${event.values.firstOrNull()}")
        val nowMs = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val raw = event.values.firstOrNull() ?: return
                val bpm = raw.toInt()

                if (bpm <= 0) {
                    if (lastValidBpm > 0 && nowMs - lastValidBpmTsMs <= HOLD_MS) {
                        Log.d("Vitals", "HR=0 ignored (hold last=$lastValidBpm)")
                    } else {
                        Log.d("Vitals", "HR=0 ignored (no recent valid)")
                    }
                    return
                }

//                heartRate = event.values.firstOrNull() ?: return
                lastValidBpm = bpm
                lastValidBpmTsMs = nowMs
//                sendHrNow(heartRate.toInt(), nowMs)

                WatchHrBus.post(bpm)
                sendHrNow(bpm, nowMs)

                if (!hasHeartBeatSensor()) {
//                    val approxIbi = (60000f / max(heartRate, 1f)).toInt()
                    val approxIbi = (60000f / max(bpm.toFloat(), 1f)).toInt()
                    addIbi(approxIbi)
                }
            }
            Sensor.TYPE_HEART_BEAT -> {
                val tsNs = event.timestamp
                lastBeatNs?.let { prev ->
                    val ibiMs = ((tsNs - prev) / 1_000_000L).toInt()
                    if (ibiMs in 300..2000) {
                        addIbi(ibiMs)
                    }
                }
                lastBeatNs = tsNs
            }
        }

        if (nowMs - lastSendMs >= sendIntervalMs && ibiBufferMs.isNotEmpty()) {
            sendIbiBatch(ibiBufferMs.toList(), nowMs)
            Log.d("Vitals", "IBI batch send n=${ibiBufferMs.size}")
            ibiBufferMs.clear()
            lastSendMs = nowMs
        }
    }

    private fun addIbi(ibiMs: Int) {
        if (ibiBufferMs.isEmpty() || kotlin.math.abs(ibiBufferMs.last() - ibiMs) < 300) {
            ibiBufferMs += ibiMs
        }
    }

    private fun hasHeartBeatSensor(): Boolean =
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_BEAT) != null

    // ---- 전송 ----
    private fun sendHrNow(bpm: Int, tsMs: Long) {
        val bb = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(tsMs)
        bb.putInt(bpm)
        val payload = "$bpm|$tsMs"
//        WearTx.sendMessage(this, "/hr", bb.array())
        WearTx.sendMessage(this, "/hr", payload.toByteArray())

    }

    private fun sendIbiBatch(ibis: List<Int>, ts0Ms: Long) {
        val n = ibis.size
        val bb = ByteBuffer.allocate(8 + 4 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(ts0Ms)        // 배치 기준 시각
        bb.putInt(n)
        ibis.forEach { bb.putShort(it.toShort()) } // 0~32767ms 가정, 2바이트
        WearTx.sendMessage(this, "/ibi", bb.array())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun startForegroundWithNotif() {
        // 채널 생성 + startForeground(...) 구현 (생략)
        val channelId = "vitals_channel"
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "심박 측정",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }

        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("심박 측정 중")
            .setContentText("화면이 꺼져도 측정이 계속됩니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(1, notif)
    }
}