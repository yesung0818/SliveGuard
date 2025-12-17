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

//        if (!loginId.isNullOrEmpty()) {
//            DrowsyMonitoringService.start(this)
//            startActivity(this, MainActivity::class.java)
//        } else {
//            startActivity(this, LoginActivity::class.java)
//        }

//        val nextIntent = if (token.isNullOrEmpty()) {
        val nextIntent = if (loginId.isNullOrEmpty()) {
            Intent(this, LoginActivity::class.java)
        } else {
            if (loginType == SessionManager.LoginType.NORMAL.name && !accessToken.isNullOrBlank()) {
                DrowsyMonitoringService.start(this)
            }
//            DrowsyMonitoringService.start(this)
            Intent(this, MainActivity::class.java)
        }

        startActivity(nextIntent)
        finish()
    }
}