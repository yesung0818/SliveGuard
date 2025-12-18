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
import androidx.transition.Visibility
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.yskim.sliveguardproject.databinding.FragmentHomeBinding
import com.yskim.sliveguardproject.login.SessionManager
import com.yskim.sliveguardproject.record.room.AppDb
import com.yskim.sliveguardproject.wear.DrowsyStateBus
import com.yskim.sliveguardproject.wear.HrBus
import com.yskim.sliveguardproject.wear.HrvBus
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Date
import java.util.Locale


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var sum = 0
    private var count = 0
    private var maxHr = 0

    private val dao by lazy { AppDb.get(requireContext()).hrRecordDao() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupLineChart()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    HrBus.bpm.collect { bpm ->
                        bpm?.let {
                            binding.tvCurrentBpm.text = "$it bpm"
                            binding.tvLastSync.visibility = View.VISIBLE
                            binding.tvLastSync.text = "마지막 동기화: " + SimpleDateFormat("a h:mm", Locale.KOREA).format(Date())
                            sum += it; count++
                            if (it > maxHr) maxHr = it
                            binding.tvAvgHr.text = "${sum / count} bpm"
                            binding.tvRestHr.text = "$maxHr bpm"
                        }
                    }
                }

                launch {
                    val today = LocalDate.now().toString()
                    dao.flowByDate(today).collect { list ->
                        val ordered = list.asReversed()
                        renderHrChart(
                            times = ordered.map { it.time },
                            hrs = ordered.map { it.hr }
                        )
                    }
                }

                launch {
                    DrowsyStateBus.state.collect { s ->
                        binding.tvCurrentState.text = s?.stage ?: "측정 중..."
                    }
                }

                launch {
                    HrvBus.state.collect { st ->
                        val measuring = SessionManager.isMeasuring(requireContext())
                        if (!measuring) {
                            binding.tvDrowsyStatus.text = "대기 중"
                            return@collect
                        }

                        binding.tvDrowsyStatus.text =
                            if (!st.isBaselineReady) "준비 중 (베이스라인 측정 중, 약 3분)"
                            else "측정 중"

//                        binding.tvDrowsyStatus.text = when {
//                            !measuring -> "대기 중 (워치에서 측정 시작을 누르세요)"
//                            !st.isBaselineReady -> "준비 중 (베이스라인 측정 중, 약 3분)"
//                            else -> "측정 중"
//                        }
                    }
                }
            }
        }
    }

    private fun setupLineChart() = with(binding.lineChart) {
        description.isEnabled = false
        legend.isEnabled = false
        axisRight.isEnabled = false

        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)

        axisLeft.setDrawGridLines(true)
        axisLeft.axisMinimum = 50f
        axisLeft.axisMaximum = 120f

        setTouchEnabled(false)
        isDragEnabled = false
        setScaleEnabled(false)
        setPinchZoom(false)
        isDoubleTapToZoomEnabled = false

        setDrawGridBackground(false)
    }

    private fun renderHrChart(times: List<String>, hrs: List<Int>) {
        val entries = hrs.mapIndexed { idx, hr -> Entry(idx.toFloat(), hr.toFloat()) }

        val dataSet = LineDataSet(entries, "HR").apply {
            setDrawCircles(false)
            setDrawValues(false)
            lineWidth = 2f
        }

        binding.lineChart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val i = value.toInt()
                return times.getOrNull(i) ?: ""
            }
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.invalidate()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}