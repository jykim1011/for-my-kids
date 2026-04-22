package com.formykids.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.formykids.FirestoreManager
import com.formykids.databinding.FragmentAlertHistoryBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertHistoryFragment : Fragment() {
    private var _binding: FragmentAlertHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            if (!FirestoreManager.isPremium()) {
                binding.listAlerts.adapter = ArrayAdapter(
                    requireContext(), android.R.layout.simple_list_item_1,
                    listOf("프리미엄 플랜에서 알림 이력을 확인할 수 있습니다.")
                )
                return@launch
            }
            val user = FirestoreManager.getCurrentUser() ?: return@launch
            val familyId = user["familyId"] as? String ?: return@launch
            val alerts = FirestoreManager.getAlerts(familyId)
            val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
            val items = alerts.map { alert ->
                val ts = (alert["timestamp"] as? Long) ?: 0L
                val type = when (alert["type"]) { "scream" -> "비명"; "cry" -> "울음"; else -> "큰소리" }
                val conf = ((alert["confidence"] as? Double)?.times(100))?.toInt() ?: 0
                "${fmt.format(Date(ts))}  $type (${conf}%)"
            }
            binding.listAlerts.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
