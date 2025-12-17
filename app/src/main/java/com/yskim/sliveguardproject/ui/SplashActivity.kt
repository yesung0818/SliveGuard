package com.yskim.sliveguardproject.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yskim.sliveguardproject.Main.MainActivity
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.service.DrowsyMonitoringService

class SplashActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginId = SessionManager.getLoginId(this)

        val loginType = SessionManager.getLoginType(this)

        val accessToken = SessionManager.getAccessToken(this)

        val nextIntent = if (loginId.isNullOrEmpty()) {
            Intent(this, LoginActivity::class.java)
        } else {
//            if (?loginType == SessionManager.LoginType.NORMAL.name && !accessToken.isNullOrBlank()) {
            if (loginType == SessionManager.LoginType.NORMAL.name) {
                    DrowsyMonitoringService.start(this)
            }
            Intent(this, MainActivity::class.java)
        }

        startActivity(nextIntent)
        finish()
    }
}