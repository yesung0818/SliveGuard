package com.yskim.sliveguardproject.presentation

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

private const val TAG = "HR-WEAR"

class HrMeasureRepo(ctx: Context) {

    private val appCtx = ctx.applicationContext
    private val measureClient = HealthServices.getClient(appCtx).measureClient
    private var callback: MeasureCallback? = null

    // 클래스 스코프(취소는 필요 시 별도 메서드에서)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(onHr: (Int) -> Unit) {
        if (callback != null) return
        Log.d(TAG, "registerMeasureCallback: HEART_RATE_BPM")

        callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                if (availability is DataTypeAvailability) {
                    Log.d(TAG, "availability=$availability for $dataType")
                }
            }

            override fun onDataReceived(data: DataPointContainer) {
                val points = data.getData(DataType.HEART_RATE_BPM)
                if (points.isNotEmpty()) {
                    val bpm = points.last().value.toInt()

                    if (bpm <= 0) {
                        Log.d(TAG, "HR sample bpm=$bpm ignored")
                        return
                    }
                    Log.i(TAG, "HR sample bpm=$bpm")
                    onHr(bpm)
                }
            }
        }

        scope.launch {
            try {
                measureClient.registerMeasureCallback(
                    DataType.HEART_RATE_BPM,
                    ContextCompat.getMainExecutor(appCtx),   // ✅ App context executor
                    callback!!
                )
                Log.d(TAG, "registerMeasureCallback: SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "registerMeasureCallback: FAIL", e)
            }
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        scope.launch {
            try {
                measureClient
                    .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, cb)
                    .await()
                Log.d(TAG, "unregister: SUCCESS")
            } catch (e: Exception) {
                Log.e(TAG, "unregister: FAIL", e)
            }
        }
    }
}