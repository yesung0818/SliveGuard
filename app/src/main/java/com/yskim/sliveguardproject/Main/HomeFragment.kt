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
import com.yskim.sliveguardproject.wear.HrBus
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
        }


    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }


}