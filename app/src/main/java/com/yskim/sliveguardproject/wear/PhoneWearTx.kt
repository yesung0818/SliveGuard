package com.yskim.sliveguardproject.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable

object PhoneWearTx {

    private const val TAG = "PHONE_TX"

    fun sendToWatch(ctx: Context, path: String, data: ByteArray) {
        val nodeClient = Wearable.getNodeClient(ctx)
        val msgClient = Wearable.getMessageClient(ctx)

        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected watch nodes")
                    return@addOnSuccessListener
                }
                nodes.forEach { node ->
                    msgClient.sendMessage(node.id, path, data)
                        .addOnSuccessListener { Log.d(TAG, "sent path=$path to ${node.displayName}") }
                        .addOnFailureListener { e -> Log.e(TAG, "send failed path=$path", e) }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "connectedNodes failed", e )
            }
    }
}