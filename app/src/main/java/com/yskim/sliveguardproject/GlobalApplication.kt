package com.yskim.sliveguardproject

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kakao.sdk.common.KakaoSdk

class GlobalApplication: Application() {
    override fun onCreate() {
        super.onCreate()

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        KakaoSdk.init(this, "75c1deacc24ac0271812f2f7200489cd")
    }
}