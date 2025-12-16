package com.yskim.sliveguardproject.presentation.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject

object WearTx {
    fun sendMessage(ctx: Context, path: String, payload: ByteArray) {
//        val client = Wearable.getNodeClient(ctx)
//        client.connectedNodes.addOnSuccessListener { nodes ->
//            nodes.forEach { node ->
//                Wearable.getMessageClient(cVitalsServicetx).sendMessage(node.id, path, payload)
//            }
//        }
        val nodesTask = Wearable.getNodeClient(ctx).connectedNodes
        nodesTask.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) Log.w("WearTx", "No connected nodes!" )
            nodes.forEach { node ->
                Wearable.getMessageClient(ctx).sendMessage(node.id, path, payload)
                    .addOnSuccessListener { Log.d("WearTx","send ok $path size=${payload.size}") }
                    .addOnFailureListener { t -> Log.e("WearTx","send fail $path", t) }
            }
        }.addOnFailureListener { t ->
            Log.e("WearTx","nodes fail", t)
        }
    }

    fun sendDeviceInfo(ctx: Context) {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val json = JSONObject().apply {
            put("deviceName", deviceName)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
        }.toString()

        sendMessage(
            ctx = ctx,
            path = "/device/info",
            payload = json.toByteArray(Charsets.UTF_8)
        )
    }
}