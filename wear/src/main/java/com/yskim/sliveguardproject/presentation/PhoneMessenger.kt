package com.yskim.sliveguardproject.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

private const val TAG = "HR-WEAR"
object Paths { const val HR = "/hr" }

class PhoneMessenger(private val context: Context) {
    @Volatile private var nodeId: String? = null

    suspend fun init() {
        val nodes = Wearable.getNodeClient(context).connectedNodes.await()
        Log.d(TAG, "connectedNodes(WATCH)=${nodes.map { it.displayName to it.id }}")
        nodeId = nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
        Log.d(TAG, "selected nodeId=$nodeId")
    }

    fun sendHr(bpm: Int) {
        val id = nodeId
        if (id == null) {
            Log.w(TAG, "sendHr: nodeId is null (phone not connected?)")
            return
        }
        val payload = bpm.toString().encodeToByteArray()
        Wearable.getMessageClient(context)
            .sendMessage(id, Paths.HR, payload)
            .addOnSuccessListener { Log.d(TAG, "send /hr bpm=$bpm -> OK(msgId=$it)") }
            .addOnFailureListener { Log.e(TAG, "send /hr bpm=$bpm -> FAIL", it) }
    }

    fun sendIbiBatch(ts0Ms: Long, ibis: IntArray) {
        val n = ibis.size
        if (n == 0) return
        val bb = java.nio.ByteBuffer.allocate(12 + n * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        bb.putLong(ts0Ms)
        bb.putInt(n)
        ibis.forEach { bb.putShort(it.toShort()) } // unsigned short 취급

        com.yskim.sliveguardproject.presentation.util.WearTx
            .sendMessage(context, "/ibi", bb.array())
    }

    fun isReady(): Boolean = nodeId != null
}