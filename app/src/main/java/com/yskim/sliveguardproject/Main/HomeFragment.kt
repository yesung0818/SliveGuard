package com.yskim.sliveguardproject.Main

import android.icu.text.SimpleDateFormat
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yskim.sliveguardproject.databinding.FragmentHomeBinding
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.wear.HrBus
import com.yskim.sliveguardproject.wear.HrvBus
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var sum = 0
    private var count = 0
    private var maxHr = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    HrBus.bpm.collect { bpm ->
                        bpm?.let {
                            binding.tvCurrentBpm.text = "$it bpm"
                            binding.tvLastSync.text = "마지막 동기화: " + SimpleDateFormat("a h:mm", Locale.KOREA).format(Date())
                            sum += it; count++
                            if (it > maxHr) maxHr = it
                            binding.tvAvgHr.text = "${sum / count} bpm"
                            binding.tvRestHr.text = "$maxHr bpm"
                            binding.tvCurrentState.text = when {
                                it < 60 -> "낮음"
                                it < 100 -> "정상"
                                else -> "높음"
                            }
                        }
                    }
                }

                launch {
                    HrvBus.state.collect { st ->
                        val measuring = SessionManager.isMeasuring(requireContext())
                        binding.tvDrowsyStatus.text = when {
                            !measuring -> "대기 중 (워치에서 측정 시작을 누르세요)"
                            !st.isBaselineReady -> "준비 중 (베이스라인 측정 중, 약 3분)"
                            else -> "측정 중"
                        }
//                        if (!st.isBaselineReady) {
//                            binding.tvDrowsyStatus.text = "준비 중 (베이스라인 측정 중, 약 3분)"
//                        } else {
//                            binding.tvDrowsyStatus.text = "측정 중"
//                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}