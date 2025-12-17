package com.yskim.sliveguardproject.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.wear.widget.ArcLayout
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.databinding.ActivityHrBinding
import com.yskim.sliveguardproject.presentation.service.VitalsService
import com.yskim.sliveguardproject.presentation.util.WearTx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.jvm.java

private const val TAG = "HR-WEAR"
private const val REQ_SENSORS = 1001

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var binding : ActivityHrBinding
    private lateinit var repo: HrMeasureRepo
    private lateinit var messenger: PhoneMessenger

    private val ibiBuf = ArrayList<Int>(32)
    private var lastIbiSentAt = 0L

    private var canBackgroundMeasure = false

    private val perms = arrayOf(
        Manifest.permission.BODY_SENSORS,
//        Manifest.permission.BODY_SENSORS_BACKGROUND
    )

    private var isMeasuring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHrBinding.inflate(layoutInflater)
        setContentView(binding.root)   // ✅ XML 레이아웃 사용

        Log.d(TAG, "SDK_INT=${Build.VERSION.SDK_INT}")

        repo = HrMeasureRepo(this)
        messenger = PhoneMessenger(this)

        binding.btnToggle.isEnabled = false
        binding.tvStatus.text = "폰 연결 확인중..."

        // 연결 노드 준비
        scope.launch {
            messenger.init()
            if (messenger.isReady()) {
                binding.tvStatus.text = "연결됨"
                binding.btnToggle.isEnabled = true
            } else {
                binding.tvStatus.text = "폰 연결 안됨"
            }
//            startIfPermissionGranted()
            
            val hs = HealthServices.getClient(this@MainActivity)
            val caps = hs.measureClient.getCapabilitiesAsync().await()
            Log.d("HS-CAPS", "supported measure types = ${caps.supportedDataTypesMeasure}")
        }
        binding.btnToggle.setOnClickListener {
            if (isMeasuring) stopMeasuring() else startMeasuring()
        }
    }

    private fun startMeasuring() {
        val perms = requiredSensorPermissions()
        val notGranted = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQ_SENSORS)
            return
        }
        if (!messenger.isReady()) {
            binding.tvStatus.text = "폰 미연결"
            return
        }

        // OS 버전에 따라 모드 분기
        if (Build.VERSION.SDK_INT <= 32) {
            // 워치4 → 서비스 모드 (화면 꺼져도)
            canBackgroundMeasure = true
            startServiceMode()
        } else {
            // 워치6 → 화면 켜진 상태 모드만
            canBackgroundMeasure = false
            startForegroundOnlyMode()
        }

        // 여기까지 왔다는 건 OS 버전에 맞는 심박 권한은 다 허용된 상태

        // HR 측정 서비스 시작 (화면 꺼져도 유지)
//        val svcIntent = Intent(this, VitalsService::class.java)
//        ContextCompat.startForegroundService(this, svcIntent)
//
//        binding.tvStatus.text = "측정 중...(서비스)"
//        binding.btnToggle.text = "측정 종료"
//        isMeasuring = true
    }

    private fun stopMeasuring() {
        if (canBackgroundMeasure) {
            stopServiceMode()
        } else {
            stopForegroundOnlyMode()
        }
//        repo.stop()
//        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//        stopService(Intent(this, VitalsService::class.java))
        binding.tvStatus.text = "정지됨"
        binding.btnToggle.text = "측정 시작"
        isMeasuring = false
    }

    override fun onRequestPermissionsResult(
        rc: Int,
        perms: Array<out String>,
        res: IntArray
    ) {
        Log.d(TAG, "requested=${perms.joinToString()}")
        perms.forEach {
            Log.d(TAG, "checkSelf($it)=${ContextCompat.checkSelfPermission(this, it)}")
        }
        Log.d(TAG, "grantResults=${res.joinToString()}")

        super.onRequestPermissionsResult(rc, perms, res)
        Log.d(TAG, "onRequestPermissionsResult rc=$rc perms=${perms.joinToString()} res=${res.joinToString()}")
        if (rc == REQ_SENSORS) {
            Log.d(TAG, "onRequestPermissionsResult: rc==$rc granted=${res.joinToString()}")
            if (res.isNotEmpty() && res.all { it == PackageManager.PERMISSION_GRANTED }) {
                startMeasuring()
            } else {
                binding.tvStatus.text = "권한 거부됨"
            }
//            startMeasuring()
        }
    }

    private fun requiredSensorPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT > 34) {
            arrayOf("android.permission.health.READ_HEART_RATE")
        } else {
            arrayOf(Manifest.permission.BODY_SENSORS)
        }
    }

    private fun startServiceMode() {
        val svcIntent = Intent(this, VitalsService::class.java)
        ContextCompat.startForegroundService(this, svcIntent)

        WearTx.sendMessage(this, "/start_measure", ByteArray(0))
        WearTx.sendDeviceInfo(this)

        binding.tvStatus.text = "측정 중...(백그라운드 지원)"
        binding.btnToggle.text = "측정 종료"
        isMeasuring = true
    }

    private fun stopServiceMode() {
        stopService(Intent(this, VitalsService::class.java))

        WearTx.sendMessage(this, "/stop_measure", ByteArray(0))

        binding.tvStatus.text = "정지됨"
        binding.btnToggle.text = "측정 시작"
        isMeasuring = false
    }

    private fun startForegroundOnlyMode() {
//        if (repo == null) repo = HrMeasureRepo(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        repo.start { bpm ->
            runOnUiThread { binding.tvHr.text = "$bpm bpm" }
            messenger.sendHr(bpm)

            val bpmi = bpm.coerceAtLeast(1)
            val approxIbi = (60000f / bpmi).toInt().coerceIn(300, 2000)
            // 필요하면 여기서도 IBI 추정해서 batch로 보낼 수 있음
            ibiBuf += approxIbi

            val now = SystemClock.elapsedRealtime()
            if (now - lastIbiSentAt >= 2_000 && ibiBuf.isNotEmpty()) {
                messenger.sendIbiBatch(now,ibiBuf.toIntArray())
                Log.d(TAG, "IBI batch sent n=${ibiBuf.size}")
                ibiBuf.clear()
                lastIbiSentAt = now
            }
        }

        binding.tvStatus.text = "측정 중..."
        binding.btnToggle.text = "측정 종료"
        isMeasuring = true
    }

    private fun stopForegroundOnlyMode() {
        repo.stop()
//        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.tvStatus.text = "정지됨"
        binding.btnToggle.text = "측정 시작"
        isMeasuring = false
    }

    override fun onDestroy() {
        super.onDestroy()
        repo.stop()
        scope.cancel()
    }
}
