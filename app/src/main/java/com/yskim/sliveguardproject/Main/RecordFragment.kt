package com.yskim.sliveguardproject.Main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ksensordevicedesign.ksensorproject.util.setOnSingleClickListener
import com.yskim.sliveguardproject.databinding.FragmentRecordBinding
import com.yskim.sliveguardproject.record.RecordAdapter
import com.yskim.sliveguardproject.record.RecordRow
import com.yskim.sliveguardproject.wear.HrvBus
import com.yskim.sliveguardproject.wear.VitalsBus
import kotlinx.coroutines.launch
import java.time.LocalDate

class RecordFragment : Fragment() {
    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private val adapter = RecordAdapter()

    private val today: LocalDate = LocalDate.now()
    private var currentDate: LocalDate = LocalDate.now()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvRecords.adapter = adapter
        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())

        binding.btnPrevDay.setOnClickListener {
            currentDate = currentDate.minusDays(1)
            renderDay(currentDate)
        }

        binding.btnNextDay.setOnClickListener {
            if (currentDate.isBefore(today)) {
                currentDate = currentDate.plusDays(1)
                renderDay(currentDate)
            }
        }
        renderDay(currentDate)

//        // Test : 실시간 데이터 표시
//        viewLifecycleOwner.lifecycleScope.launch {
//            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    VitalsBus.state.collect { s ->
//                        binding.tvHr.text = "HR — ${s.hrBpm?.toString() ?: "--"} bpm"
//                        binding.tvIbi.text = "IBI — ${s.lastIbiMs?.toString() ?: "--"} ms"
//                        binding.tvIbiRecent.text =
//                            "최근 IBI 5개: " + (if (s.recentIbiMs.isEmpty()) "-" else s.recentIbiMs.joinToString(", ") + " ms")
//                    }
//                }
//                launch {
//                    HrvBus.state.collect { h ->
//                        binding.tvHrvScore.text = h.sHrv?.let { String.format("%.2f", it) ?: "--"}
//                        binding.tvHrvBase.text   = "기준 RMSSD=${h.baselineRmssdMs?.toInt() ?: 0} ms, SDNN=${h.baselineSdnnMs?.toInt() ?: 0} ms"
//                        binding.tvHrvCurrent.text= "현재 RMSSD=${h.currentRmssdMs?.toInt() ?: 0} ms, SDNN=${h.currentSdnnMs?.toInt() ?: 0} ms"
//
//                         val status = when {
//                             h.sHrv == null -> "기준 측정 중"
//                             h.sHrv < 0.4   -> "정상"
//                             h.sHrv < 0.6   -> "주의"
//                             else           -> "졸음 의심"
//                         }
//                         binding.tvHrvStatus.text = status
//                    }
//                }
//
//            }
//        }
    }

    private fun renderDay(date: LocalDate) {
        binding.tvDate.text = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

        val canGoNext = date.isBefore(today)
        binding.btnNextDay.isEnabled = canGoNext
        binding.btnNextDay.alpha = if (canGoNext) 1.0f else 0.35f

        // TODO(서버 준비되면): data로 서버 조회해서 rows를 받아오기
        val rows = fakeRows()

        val hrs = rows.map { it.hr }
        val avg = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
        val min = hrs.minOrNull() ?: 0
        val max = hrs.maxOrNull() ?: 0
        val current = hrs.firstOrNull() ?: 0

        binding.tvAvgHr.text = "${avg} bpm"
        binding.tvMinHr.text = "${min} bpm"
        binding.tvMaxHr.text = "${max} bpm"
        binding.tvCurrentHr.text = "${current} bpm"

        adapter.submit(rows)
    }

    // 더미 데이터 (최신 위로 오게)
    private fun fakeRows(): List<RecordRow> {
        return listOf(
            RecordRow("10:20", 76, "정상", false),
            RecordRow("10:10", 80, "정상", false),
            RecordRow("10:04", 72, "졸음 1단계", true),
            RecordRow("10:00", 77, "정상", false),
            RecordRow("09:50", 77, "정상", false),
            RecordRow("09:40", 79, "정상", false),
            RecordRow("09:30", 75, "정상", false),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}