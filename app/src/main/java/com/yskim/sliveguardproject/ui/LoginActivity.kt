package com.yskim.sliveguardproject.ui

import android.R.attr.data
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.se.omapi.Session
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.ksensordevicedesign.ksensorproject.util.setOnSingleClickListener
import com.yskim.sliveguardproject.Main.MainActivity
import com.yskim.sliveguardproject.databinding.ActivityLoginBinding
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.network.auth.AuthApiClient
import com.yskim.sliveguardproject.network.auth.KakaoLoginRequest
import com.yskim.sliveguardproject.network.auth.LoginRequest
import com.yskim.sliveguardproject.network.auth.StartStopMeasurementRequest
import com.yskim.sliveguardproject.service.DrowsyMonitoringService
import kotlinx.coroutines.launch
import java.util.jar.Manifest

class LoginActivity: AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())

        // 라이트 테마 고정이면 아이콘 어둡게
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnSingleClickListener {
            doNormalLogin()
        }

        binding.btnGotoSignup.setOnSingleClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        binding.btnKakaoLogin.setOnSingleClickListener {
            doKakaoLogin()
        }
    }

    // --------------- 일반 로그인 -----------------

    private fun doNormalLogin() {
        val id = binding.etLoginId.text.toString().trim()
        val pw = binding.etLoginPassword.text.toString().trim()

        if (id.isEmpty() || pw.isEmpty()) {
            Toast.makeText(this, "아이디와 비밀번호를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val res = AuthApiClient.api.login(LoginRequest(id, pw))

                Toast.makeText(this@LoginActivity, res.message, Toast.LENGTH_SHORT).show()
                if (!res.ok) return@launch

                val data = res.data.orEmpty()
                val userName = (data["name"] as? String)
                    ?: (data["nickname"] as? String)
                    ?: id

                onLoginSuccessCommon(
                    loginType = SessionManager.LoginType.NORMAL,
                    loginId = id,
                    userName = userName
                )

//                val accessToken = (data["accessToken"] as? String).orEmpty()

            } catch (e: Exception) {
                Log.e("LOGIN", "login error", e)
                Toast.makeText(this@LoginActivity, "로그인 실패", Toast.LENGTH_SHORT).show()
            }
        }

//        // 일단 샘플로 “로그인 성공했다고 가정”하고 토큰 저장
//        val fakeToken = "FAKE_TOKEN_FOR_TEST"
//        SessionManager.saveAccessToken(this, fakeToken)
//        goToMain()
    }

    // --------------- 카카오 로그인 -----------------
    private fun doKakaoLogin() {
        if (UserApiClient.instance.isKakaoTalkLoginAvailable(this)) {
            UserApiClient.instance.loginWithKakaoTalk(this) { token, error ->
                if (error != null) {
                    Log.e("KAKAO", "카카오 로그인 실패", error)

                    if (error is ClientError && error.reason == ClientErrorCause.Cancelled) {
                        // 사용자가 카카오톡 로그인 창에서 취소한 경우
                        return@loginWithKakaoTalk
                    }

                    // 그 외에는 카카오계정(웹) 로그인 시도
                    loginWithKakaoAccount()
                } else if (token != null) {
                    afterKakaoLogin(token)
                }
            }
        } else {
            loginWithKakaoAccount()
        }
    }

    private fun loginWithKakaoAccount() {
        UserApiClient.instance.loginWithKakaoAccount(this) { token, error ->
            if (error != null) {
                Log.e("KAKAO", "카카오 계정 로그인 실패", error)
            } else if (token != null) {
                afterKakaoLogin(token)
            }
        }
    }

    private fun afterKakaoLogin(token: OAuthToken) {
        Log.d("KAKAO", "accessToken=${token.accessToken}")

        // 카카오 로그인 성공 후 처리
//        lifecycleScope.launch {
//            try {
//                val res = AuthApiClient.api.kakaoLogin(
//                    KakaoLoginRequest(token.accessToken)
//                )
////
////                SessionManager.saveAccessToken(this@LoginActivity, res.accessToken)
////                SessionManager.saveUserName(this@LoginActivity, res.user.name)
////
////                goToMain()
//                onLoginSuccess(
//                    accessToken = res.accessToken,
//                    userName = res.user.name
//                )
//
//            } catch (e: Exception) {
//                Log.e("KAKAO_LOGIN", "kakao login error", e)
//                Toast.makeText(this@LoginActivity, "카카오 로그인 실패", Toast.LENGTH_SHORT).show()
//            }
//        }
        UserApiClient.instance.me { user, error ->
            if (error != null || user == null) {
                Toast.makeText(this, "카카오 사용자 정보 조회 실패", Toast.LENGTH_SHORT).show()
                return@me
            }

            val kakaoUserId = user.id?.toString() ?: return@me
            val name = user.kakaoAccount?.profile?.nickname ?: "카카오사용자"

//            SessionManager.saveAccessToken(this, token.accessToken)
            SessionManager.saveKakaoAccessToken(this, token.accessToken)

            onLoginSuccessCommon(
                loginType = SessionManager.LoginType.KAKAO,
                loginId = kakaoUserId,   // ★ 핵심
                userName = name
            )
        }

        // 지금은 샘플로 바로 저장만:
//        SessionManager.saveAccessToken(this, "FAKE_KAKAO_LOGIN_TOKEN")
//        goToMain()
    }

//    private fun onLoginSuccess(accessToken: String, userName: String, loginId: String) {
//        if (accessToken.isNotBlank()) {
//            SessionManager.saveAccessToken(this, accessToken)
//        }
//
////        SessionManager.saveAccessToken(this, accessToken)
//        SessionManager.saveUserName(this, userName)
////        SessionManager.saveLoginId(this, loginId)
//
//        DrowsyMonitoringService.start(this)
//
//        goToMain()
//    }

    private fun onLoginSuccessCommon(
        loginType: SessionManager.LoginType,
        loginId: String,
        userName: String,
//        kakaoAccessToken: String? = null
    ) {
//        SessionManager.saveLoginType(this, SessionManager.LoginType.KAKAO.name)
        SessionManager.saveLoginType(this, loginType.name)
        SessionManager.saveLoginId(this, loginId)
        SessionManager.saveUserName(this, userName)

//        if (!kakaoAccessToken.isNullOrBlank()) {
//            SessionManager.saveKakaoAccessToken(this, kakaoAccessToken)
//        }

        DrowsyMonitoringService.start(this)
        goToMain()
    }


    // --------------- 메인 화면 이동 -----------------
    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}