package com.yskim.sliveguardproject.record

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.yskim.sliveguardproject.R
import com.yskim.sliveguardproject.databinding.ItemRecordRowBinding

data class RecordRow(
    val time: String,
    val hr: Int,
    val stage: String,
    val isAlert: Boolean
)

class RecordAdapter: RecyclerView.Adapter<RecordAdapter.VH>() {
    private val items = mutableListOf<RecordRow>()

    fun submit(list: List<RecordRow>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(
            ItemRecordRowBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    class VH(
        private val binding: ItemRecordRowBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: RecordRow) {
            binding.tvTime.text = row.time
            binding.tvHr.text = "${row.hr} bpm"
            binding.tvStage.text = row.stage

            val color = if (row.isAlert) 0xFFFF3B30.toInt() else 0xFF222222.toInt()
            binding.tvTime.setTextColor(color)
            binding.tvHr.setTextColor(color)
            binding.tvStage.setTextColor(color)
        }

    }


}