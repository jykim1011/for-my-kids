package com.formykids.parent

import android.app.DownloadManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.formykids.R
import com.formykids.databinding.ItemAlertBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter(private val context: Context, private val items: List<Map<String, Any?>>) :
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
            "scream" -> context.getString(R.string.alert_type_scream)
            "cry" -> context.getString(R.string.alert_type_cry)
            else -> context.getString(R.string.alert_type_loud)
        }
        val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0

        holder.binding.tvAlertTime.text = fmt.format(Date(ts))
        holder.binding.tvAlertType.text = type
        holder.binding.tvAlertConfidence.text = context.getString(R.string.alert_confidence_format, conf)

        val chipColor = when (alert["type"]) {
            "scream" -> 0xFFEF4444.toInt()
            "cry" -> 0xFFF59E0B.toInt()
            else -> 0xFF2563EB.toInt()
        }
        (holder.binding.tvAlertType.background.mutate() as? GradientDrawable)?.setColor(chipColor)

        val clipUrl = alert["clipUrl"] as? String
        holder.binding.layoutClipActions.visibility = if (clipUrl != null) View.VISIBLE else View.GONE

        if (clipUrl != null) {
            holder.binding.btnPlayClip.setOnClickListener {
                val player = MediaPlayer()
                player.setDataSource(clipUrl)
                player.prepareAsync()
                player.setOnPreparedListener { it.start() }
                player.setOnCompletionListener { it.release() }
            }
            holder.binding.btnDownloadClip.setOnClickListener {
                val req = DownloadManager.Request(Uri.parse(clipUrl)).apply {
                    setTitle("ForMyKids_${fmt.format(Date(ts))}.m4a")
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "ForMyKids_${ts}.m4a"
                    )
                }
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
                    .enqueue(req)
            }
        }
    }

    override fun getItemCount() = items.size
}
