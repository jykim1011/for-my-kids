package com.formykids.parent

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.formykids.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter(private val items: List<Map<String, Any?>>) :
    RecyclerView.Adapter<AlertAdapter.ViewHolder>() {

    private val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

    inner class ViewHolder(val binding: ItemAlertBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAlertBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val alert = items[position]
        val ts = (alert["timestamp"] as? Long) ?: 0L
        val type = when (alert["type"]) {
            "scream" -> "비명"
            "cry" -> "울음"
            else -> "큰소리"
        }
        val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0

        holder.binding.tvAlertTime.text = fmt.format(Date(ts))
        holder.binding.tvAlertType.text = type
        holder.binding.tvAlertConfidence.text = "신뢰도 ${conf}%"

        val chipColor = when (alert["type"]) {
            "scream" -> 0xFFEF4444.toInt()
            "cry" -> 0xFFF59E0B.toInt()
            else -> 0xFF2563EB.toInt()
        }
        (holder.binding.tvAlertType.background as? GradientDrawable)?.setColor(chipColor)
    }

    override fun getItemCount() = items.size
}
