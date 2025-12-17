package com.yskim.sliveguardproject.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.yskim.sliveguardproject.service.DrowsyMonitoringService
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "HR-PHONE"
class WearListenerService : WearableListenerService() {
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WearListenerService onCreate")
        Wearable.getNodeClient(this).connectedNodes
//            .addOnSuccessListener { Log.d(TAG, "connectedNodes(PHONE)=${it.map { n -> n.displayName }}") }
            .addOnSuccessListener { nodes ->
                val nodeName = nodes.firstOrNull()?.displayName
                Log.d(TAG, "connectedNodes(PHONE)=${nodes.map { it.displayName }}")

                // ★ 여기서 바로 DeviceBus에 반영 (fallback)
                if (!nodeName.isNullOrBlank()) {
                    DeviceBus.updateDeviceName(nodeName)
//                    DeviceBus.updateFromJson(
//                        """{"deviceName":"$nodeName","manufacturer":"","model":""}"""
//                    )
                }
            }
    }
    override fun onMessageReceived(e: MessageEvent) {
        Log.d("WearRx", "path=${e.path} size=${e.data?.size ?: -1}")
        when (e.path) {
            "/start_measure" -> {
//                Log.d("HRV", "Baseline reset by WATCH command")
//                HrvBus.reset()
                DrowsyMonitoringService.startMeasure(this)
            }
            "/stop_measure" -> {
                DrowsyMonitoringService.stopMeasure(this)
            }

            "/hr" -> safeHandleHr(e.data)
            "/ibi" -> safeHandleIbi(e.data)

            "/device/info" -> {
                val json = e.data?.toString(Charsets.UTF_8).orEmpty()
                DeviceBus.updateFromJson(json)
            }
        }
        Log.d(TAG, "onMessageReceived path=${e.path} from=${e.sourceNodeId} size=${e.data?.size}")
        if (e.path.startsWith("/hr")) {
//            val raw = e.data?.decodeToString()
//            val bpm = raw?.toIntOrNull()
            val bpm = e.data?.decodeToString()?.toIntOrNull()
            Log.i(TAG, "HR received: $bpm")
            bpm?.let { HrBus.post(it) }
        }
    }

    private fun handleHr(bytes: ByteArray) {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ts = bb.long
        val bpm = bb.int
        VitalsBus.postHr(ts, bpm)
    }

    private fun handleIbi(bytes: ByteArray) {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val ts0 = bb.long
        val n   = bb.int
        val ibis = IntArray(n) { bb.short.toInt() and 0xFFFF }
        if (ibis.isNotEmpty()) VitalsBus.postIbiBatch(ts0, ibis)
    }

    private fun safeHandleHr(bytes: ByteArray) {
        try {
            val now = System.currentTimeMillis()

            if (bytes.size >= 12) {
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val ts  = bb.long
                val bpm = bb.int
                VitalsBus.postHr(ts, bpm)
                return
            }

            // v2: [int bpm] (4 bytes, LE)
            if (bytes.size == 4) {
                val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val bpm = bb.int
                VitalsBus.postHr(now, bpm)
                return
            }

            // v1: ASCII 숫자 ("100" 같은) 또는 1바이트/2바이트 케이스
            val str = runCatching { String(bytes, Charsets.UTF_8).trim() }.getOrNull()
            val bpmFromAscii = str?.toIntOrNull()
            if (bpmFromAscii != null) {
                VitalsBus.postHr(now, bpmFromAscii)
                return
            }

            if (bytes.size == 1) { // unsigned byte
                val bpm = bytes[0].toInt() and 0xFF
                VitalsBus.postHr(now, bpm)
                return
            }
            if (bytes.size == 2) { // unsigned short, LE
                val bb = ByteBuffer.wrap(bytes + byteArrayOf(0,0)).order(ByteOrder.LITTLE_ENDIAN)
                val bpm = bb.int and 0xFFFF
                VitalsBus.postHr(now, bpm)
                return
            }
            Log.w(TAG,"Unknown /hr payload size=${bytes.size}")
        } catch (t: Throwable) {
            Log.e(TAG, "safeHandleHr error size=${bytes.size}", t)
        }
    }

    private fun safeHandleIbi(bytes: ByteArray) {
        try {
            // 포맷: [long ts0][int n][short IBI * n] (LE)
            if (bytes.size < 12) {
                Log.w("WearIBI", "payload too small: ${bytes.size}")
                return
            }
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val ts0 = bb.long
            val n   = bb.int
            val need = 12 + n * 2
            if (n <= 0 || need > bytes.size) {
                Log.w("WearIBI", "invalid n=$n size=${bytes.size}")
                return
            }
            val ibis = IntArray(n) { bb.short.toInt() and 0xFFFF }
            Log.d("WearIBI", "recv n=$n ts0=$ts0 last=${ibis.lastOrNull()}")
            VitalsBus.postIbiBatch(ts0, ibis)
        } catch (t: Throwable) {
            Log.e("WearIBI", "safeHandleIbi error size=${bytes.size}", t)
        }
    }
}