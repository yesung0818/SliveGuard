package com.yskim.sliveguardproject.Main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.BuildConfig
import com.google.android.gms.wearable.Wearable
import com.kakao.sdk.user.UserApiClient
import com.ksensordevicedesign.ksensorproject.util.setOnSingleClickListener
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.databinding.FragmentSettingsBinding
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.service.DrowsyMonitoringService
import com.yskim.sliveguardproject.ui.LoginActivity
import com.yskim.sliveguardproject.wear.DeviceBus
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SettingFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                DeviceBus.state.collect { st ->
                    binding.tvDeviceName.text = st.deviceName ?: "연결된 워치 없음"
                }
            }
        }

        setupProfileArea()
        setupVersion()
        setupClicks()
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val savedUri = copyToAppStorage(uri)
                if (savedUri != null) {
                    binding.imgProfile.setImageURI(savedUri)
                    SessionManager.saveProfileImageUri(requireContext(), savedUri.toString())
                }

            }
        }

    private fun copyToAppStorage(src: Uri): Uri? {
        return try {
            val ctx = requireContext()
            val resolver = ctx.contentResolver

            val outFile = File(ctx.filesDir, "profile.jpg")

            resolver.openInputStream(src).use { input ->
                if (input == null) return null
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }

            Uri.fromFile(outFile)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 상단 프로필 영역: 이름 / 이메일 표시
     * - 카카오 로그인 했으면 Kakao SDK에서 me() 호출해서 채워도 되고
     * - 일반 로그인이라면 서버에서 받은 값을 SharedPreferences 등에 저장해두고 불러오면 됨
     */
    private fun setupProfileArea() {
        val ctx = requireContext()
        // TODO: 실제 값으로 교체
        // 예시: SessionManager에서 사용자 이름/이메일을 저장했다고 가정
        val saveName = SessionManager.getUserName(ctx)
        binding.tvUserName.text = saveName ?: "사용자"
        // 필요하면 카카오에서 직접 가져오는 코드도 가능
        // (카카오 로그인 사용자만 해당)

        val savedUri = SessionManager.getProfileImageUri(ctx)
        if (!savedUri.isNullOrEmpty()) {
//            binding.imgProfile.setImageURI(Uri.parse(savedUri))
            binding.imgProfile.setImageResource(R.drawable.ic_profile_placeholder)
            return
        }

        try {
            binding.imgProfile.setImageURI(Uri.parse(savedUri))
        } catch (e: SecurityException) {
            binding.imgProfile.setImageResource(R.drawable.ic_profile_placeholder)
            SessionManager.saveProfileImageUri(ctx, "")
        } catch (e: Exception) {
            binding.imgProfile.setImageResource(R.drawable.ic_profile_placeholder)
        }

        // 4) 카카오 로그인 유저인 경우, Kakao SDK에서 닉네임 한 번 더 가져와서 업데이트
        //    (카카오로 로그인 안 했으면 이 콜백에서 user가 null이거나 에러가 날 수 있으니
        //     단순히 무시해도 된다.)
        UserApiClient.instance.me { user, error ->
            if (user != null) {
                val nickname = user.kakaoAccount?.profile?.nickname
                if (!nickname.isNullOrEmpty()) {
                    binding.tvUserName.text = nickname
                    SessionManager.saveUserName(ctx, nickname)
                }
            }
        }
    }

    private fun setupVersion() {
        val versionName = BuildConfig.VERSION_NAME
        binding.tvVersion.text = "버전 $versionName"
    }

    private fun setupClicks() {
        binding.imgProfile.setOnSingleClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Device 카드 클릭 시: 워치 연결 화면 열기 등
        binding.layoutDevice.setOnClickListener {
            // TODO: 워치 디바이스 관리 화면 열기
            Toast.makeText(requireContext(), "디바이스 설정(추후 구현)", Toast.LENGTH_SHORT).show()
        }

        // 전체 데이터 삭제
        binding.layoutDeleteAll.setOnClickListener {
            // TODO: 서버/로컬 DB 데이터 삭제 로직 추가
            Toast.makeText(requireContext(), "전체 데이터 삭제 (추후 구현)", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }
    }

    private fun logout() {
        val context = requireContext()

        // 1) 카카오 토큰도 같이 정리하고 싶으면 (카카오로 로그인한 경우)
        UserApiClient.instance.logout { /* 에러는 굳이 처리 안 해도 됨 */ }

        DrowsyMonitoringService.stop(requireContext())

        // 2) 우리 앱 세션 삭제
        SessionManager.clearSession(context)

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}