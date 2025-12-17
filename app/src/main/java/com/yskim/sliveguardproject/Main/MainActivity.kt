package com.yskim.sliveguardproject.Main

import android.R.attr.tag
import android.os.Bundle
import android.util.Log
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.databinding.ActivityMainBinding
import com.yskim.sliveguardproject.network.TestApiClient
import com.yskim.sliveguardproject.service.DrowsyMonitoringService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val fm by lazy { supportFragmentManager }

    private val TAG_HOME = "MainHome"
    private val TAG_RECORD = "Record"
    private val TAG_SETTINGS = "Settings"

    private var currentTag = TAG_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())

        // 라이트 테마 고정이면 아이콘 어둡게
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        setupBackPressExit()

        currentTag = savedInstanceState?.getString("currentTag") ?: TAG_HOME

        attachIfNeeded(TAG_HOME) { HomeFragment() }
        attachIfNeeded(TAG_RECORD) { RecordFragment() }
        attachIfNeeded(TAG_SETTINGS) { SettingFragment() }

        showOnly(currentTag)

        binding.bottomNav.selectedItemId = R.id.nav_home

        binding.bottomNav.setOnItemSelectedListener { item ->
            val nextTag = when (item.itemId) {
                R.id.nav_home -> TAG_HOME
                R.id.nav_record -> TAG_RECORD
                R.id.nav_settings -> TAG_SETTINGS
                else -> TAG_HOME
            }
            if (currentTag != nextTag) {
                showOnly(nextTag)
                currentTag = nextTag
            }
            true
        }

    }

    override fun onDestroy() {
        DrowsyMonitoringService.stop(this)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("currentTag", currentTag)
        super.onSaveInstanceState(outState)
    }

    private fun attachIfNeeded(tag: String, factory: () -> Fragment) {
        val existing = fm.findFragmentByTag(tag)
        if (existing == null) {
            val f = factory()
            fm.beginTransaction()
                .add(R.id.fragmentContainer, f, tag)
                .apply { if (tag != currentTag) hide(f) }
                .commitNow()
        }
    }

    private fun showOnly(tagToShow: String) {
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        listOf(TAG_HOME, TAG_RECORD, TAG_SETTINGS).forEach { tag ->
            fm.findFragmentByTag(tag)?.let { f ->
                if (tag == tagToShow) tx.show(f) else tx.hide(f)
            }
        }
        tx.commit()
    }

    private fun setupBackPressExit() {
        onBackPressedDispatcher.addCallback(this) {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("앱 종료")
                .setMessage("앱을 종료하시겠습니까?")
                .setPositiveButton("종료") { _, _ ->
                    finish()
                    DrowsyMonitoringService.stop(this@MainActivity)
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }
}