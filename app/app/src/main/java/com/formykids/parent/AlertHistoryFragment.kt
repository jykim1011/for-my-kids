package com.formykids.parent

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.formykids.FirestoreManager
import com.formykids.databinding.FragmentAlertHistoryBinding
import kotlinx.coroutines.launch

class AlertHistoryFragment : Fragment() {
    private var _binding: FragmentAlertHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvAlerts.layoutManager = LinearLayoutManager(requireContext())

        lifecycleScope.launch {
            if (!FirestoreManager.isPremium()) {
                binding.rvAlerts.adapter = AlertAdapter(requireContext(), emptyList())
                return@launch
            }
            val user = FirestoreManager.getCurrentUser() ?: return@launch
            val familyId = user["familyId"] as? String ?: return@launch
            val alerts = FirestoreManager.getAlerts(familyId)
            binding.rvAlerts.adapter = AlertAdapter(requireContext(), alerts)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
