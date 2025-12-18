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
import com.yskim.sliveguardproject.record.room.AppDb
import com.yskim.sliveguardproject.record.room.HrRecordEntity
import com.yskim.sliveguardproject.wear.DrowsyStateBus
import com.yskim.sliveguardproject.wear.HrvBus
import com.yskim.sliveguardproject.wear.VitalsBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class RecordFragment : Fragment() {
    private var _binding: FragmentRecordBinding? = null
    private val binding get() = _binding!!

    private val adapter = RecordAdapter()

    private val today: LocalDate = LocalDate.now()
    private var currentDate: LocalDate = LocalDate.now()

    private val dao by lazy { AppDb.get(requireContext()).hrRecordDao() }

    private val liveRows = mutableListOf<RecordRow>()

    private var winSum = 0
    private var winCnt = 0
    private var winMin = Int.MAX_VALUE
    private var winMax = Int.MIN_VALUE
    private var latestBpm: Int? = null

    private var latestStage: String = "정상"

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

        startMinuteRecorder()

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

    private fun startMinuteRecorder() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val collectJob = launch {
                    VitalsBus.state.collect { s ->
                        val bpm = s.hrBpm ?: return@collect
                        latestBpm = bpm
                        winSum += bpm
                        winCnt += 1
                        winMin = minOf(winMin, bpm)
                        winMax = maxOf(winMax, bpm)
                    }
                }

                val tickJob = launch {
                    while (true) {
                        delay(60_000L)

                        if (currentDate != today) {
                            winSum = 0; winCnt = 0
                            winMin = Int.MAX_VALUE; winMax = Int.MIN_VALUE
                            continue
                        }

                        val avg = if (winCnt > 0) (winSum / winCnt) else (latestBpm ?: 0)
                        val hhmm = LocalTime.now()
                            .withSecond(0).withNano(0)
                            .toString().substring(0, 5)

                        val row = RecordRow(
                            time = hhmm,
                            hr = avg,
//                            stage = "정상",
                            stage = latestStage,
//                            isAlert = false
                            isAlert = (latestStage != "정상")
                        )

                        liveRows.add(0, row)

                        val entity = HrRecordEntity(
                            ts = System.currentTimeMillis(),
                            date = today.toString(),
                            time = row.time,
                            hr = row.hr,
                            stage = row.stage,
                            isAlert = row.isAlert
                        )
                        launch(Dispatchers.IO) {
                            dao.upsert(entity)
                        }

                        val rowsNow = liveRows.toList()
                        adapter.submit(rowsNow)
                        updateTopStatsFromRows(liveRows)

//                        val newList = listOf(row) + emptyList()
//                        adapter.submit(newList)

                        winSum = 0; winCnt = 0
                        winMin = Int.MAX_VALUE; winMax = Int.MIN_VALUE
                    }
                }

                launch {
                    DrowsyStateBus.state.collect { st ->
                        if (st != null) {
                            latestStage = st.stage
                        }
                    }
                }

                collectJob.join()
                tickJob.join()
            }
        }
    }

    private fun renderDay(date: LocalDate) {
        binding.tvDate.text = "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"

        val canGoNext = date.isBefore(today)
        binding.btnNextDay.isEnabled = canGoNext
        binding.btnNextDay.alpha = if (canGoNext) 1.0f else 0.35f

        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                dao.listByDate(date.toString())
            }

            val rows = list.map {
                RecordRow(
                    time = it.time,
                    hr = it.hr,
                    stage = it.stage,
                    isAlert = it.isAlert
                )
            }

            if (date == today) {
                liveRows.clear()
                liveRows.addAll(rows)
            }

            updateTopStatsFromRows(rows)
            adapter.submit(rows)
        }


//        val rows = if (date == today) liveRows.toList() else fakeRows()

//        val hrs = rows.map { it.hr }
//        val avg = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
//        val min = hrs.minOrNull() ?: 0
//        val max = hrs.maxOrNull() ?: 0
//        val current = hrs.firstOrNull() ?: 0
//
//        binding.tvAvgHr.text = "${avg} bpm"
//        binding.tvMinHr.text = "${min} bpm"
//        binding.tvMaxHr.text = "${max} bpm"
//        binding.tvCurrentHr.text = "${current} bpm"


//        updateTopStatsFromRows(rows)
//        adapter.submit(rows)
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

    private fun updateTopStatsFromRows(rows: List<RecordRow>) {
        val hrs = rows.map { it.hr }
        val avg = if (hrs.isNotEmpty()) hrs.average().toInt() else 0
        val min = hrs.minOrNull() ?: 0
        val max = hrs.maxOrNull() ?: 0
        val current = hrs.firstOrNull() ?: 0

        binding.tvAvgHr.text = "${avg} bpm"
        binding.tvMinHr.text = "${min} bpm"
        binding.tvMaxHr.text = "${max} bpm"
        binding.tvCurrentHr.text = "${current} bpm"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}