package com.yskim.sliveguardproject.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.ksensordevicedesign.ksensorproject.util.setOnSingleClickListener
import com.yskim.sliveguardproject.databinding.ActivitySignupBinding
import com.yskim.sliveguardproject.network.auth.AuthApiClient
import com.yskim.sliveguardproject.network.auth.CheckDuplicateIdRequest
import com.yskim.sliveguardproject.network.auth.SignupRequest
import kotlinx.coroutines.launch

class SignupActivity: AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding

    private var isIdChecked = false
    private var lastCheckedId: String? = null
    private var isIdAvailable = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())

        // 라이트 테마 고정이면 아이콘 어둡게
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        setupListeners()
    }

    private fun setupListeners() {
        binding.etSignupId.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                isIdChecked = false
                isIdAvailable = false
                lastCheckedId = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnCheckId.setOnSingleClickListener {
            checkIdDuplicate()
        }

        binding.btnSignup.setOnSingleClickListener {
            doSignup()
        }
    }

    // 아이디 중복확인
    private fun checkIdDuplicate() {
        val id = binding.etSignupId.text.toString().trim()
        if (id.isEmpty()) {
            Toast.makeText(this, "아이디를 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val res = AuthApiClient.api.checkDuplicateId(
                    CheckDuplicateIdRequest(loginId = id)
                )

                Toast.makeText(this@SignupActivity, res.message, Toast.LENGTH_SHORT).show()

                isIdChecked = true
                lastCheckedId = id
                isIdAvailable = res.ok

            } catch (e: Exception) {
                Log.e("SIGNUP", "checkDuplicatedId error", e)
                Toast.makeText(this@SignupActivity, "서버 통신 실패", Toast.LENGTH_SHORT).show()

                isIdChecked = false
                isIdAvailable = false
                lastCheckedId = null
            }
        }
    }

    private fun doSignup() {
        val id = binding.etSignupId.text.toString().trim()
        val pw = binding.etSignupPassword.text.toString().trim()
        val pw2 = binding.etSignupPasswordConfirm.text.toString().trim()
        val name = binding.etSignupName.text.toString().trim()
        val birth = binding.etSignupBirth.text.toString().trim()

        if (id.isEmpty() || pw.isEmpty() || pw2.isEmpty() || name.isEmpty() || birth.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        if (pw != pw2) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (birth.length != 8) {
            Toast.makeText(this, "생년월일은 8자리로 입력해 주세요. (예: 20001231)", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isIdAvailable) {
            Toast.makeText(this, "이미 사용 중인 아이디입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val res = AuthApiClient.api.register(
                    SignupRequest(
                        loginId = id,
                        password = pw,
                        name = name,
                        birth = birth
                    )
                )

                Toast.makeText(this@SignupActivity, res.message, Toast.LENGTH_SHORT).show()

                if (res.ok) finish()
            } catch (e: Exception) {
                Log.e("SIGNUP", "register error", e)
                Toast.makeText(this@SignupActivity, "서버 통신 실패", Toast.LENGTH_SHORT).show()
            }
        }

        // TODO: 서버에 회원가입 API 호출
        // ApiClient.service.signup(SignupRequest(id, pw, name, birth)) ...

        // 여기서는 성공했다고 가정하고 화면 종료
//        Toast.makeText(this, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show()
//        finish() // LoginActivity로 돌아감
    }
}