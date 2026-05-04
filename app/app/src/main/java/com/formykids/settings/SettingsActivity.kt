package com.formykids.settings

import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.R
import com.formykids.FirestoreManager
import com.formykids.SplashActivity
import com.formykids.auth.RoleSelectActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import com.formykids.child.DetectionScheduleReceiver
import com.formykids.databinding.ActivitySettingsBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private var familyId: String? = null
    private var detectionSettingsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPickers()

        binding.btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            getSharedPreferences(App.PREF_NAME, MODE_PRIVATE).edit().clear().apply()
            startActivity(Intent(this, SplashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }

        binding.btnManageSubscription.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions")))
        }

        binding.btnChangePhone.setOnClickListener {
            startActivity(Intent(this, PhoneChangeActivity::class.java))
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnResetDevice.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage(getString(R.string.reset_device_confirm))
                .setPositiveButton(getString(R.string.reset_device_confirm_yes)) { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val uid = Firebase.auth.currentUser?.uid
                            if (uid != null) {
                                Firebase.firestore.collection("users").document(uid)
                                    .update(mapOf(
                                        "role" to FieldValue.delete(),
                                        "familyId" to FieldValue.delete()
                                    )).await()
                            }
                        } catch (_: Exception) {}
                        startActivity(Intent(this@SettingsActivity, RoleSelectActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                }
                .setNegativeButton(getString(R.string.btn_close), null)
                .show()
        }

        lifecycleScope.launch { loadUserSettings() }
    }

    private fun setupPickers() {
        listOf(binding.pickerStartHour, binding.pickerEndHour).forEach { picker ->
            picker.minValue = 0
            picker.maxValue = 23
            picker.displayedValues = Array(24) { "%02d:00".format(it) }
        }
        binding.pickerStartHour.value = 8
        binding.pickerEndHour.value = 22
    }

    private suspend fun loadUserSettings() {
        val user = FirestoreManager.getCurrentUser() ?: return
        val role = user["role"] as? String ?: return
        val fid = user["familyId"] as? String ?: return
        familyId = fid

        if (role == App.ROLE_PARENT) {
            binding.sectionDetection.visibility = View.VISIBLE
            detectionSettingsListener = FirestoreManager.observeDetectionSettings(fid) { enabled, schedule ->
                runOnUiThread {
                    binding.switchDetectionEnabled.isChecked = enabled
                    schedule.firstOrNull()?.let { (start, end) ->
                        binding.pickerStartHour.value = start
                        binding.pickerEndHour.value = end
                    }
                }
            }
            binding.btnSaveDetectionSchedule.setOnClickListener {
                val enabled = binding.switchDetectionEnabled.isChecked
                val start = binding.pickerStartHour.value
                val end = binding.pickerEndHour.value
                lifecycleScope.launch {
                    FirestoreManager.updateDetectionSettings(fid, enabled, listOf(start to end))
                    DetectionScheduleReceiver.rescheduleAlarms(
                        this@SettingsActivity,
                        intArrayOf(start),
                        intArrayOf(end)
                    )
                    Toast.makeText(this@SettingsActivity, getString(R.string.detection_settings_saved), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        detectionSettingsListener?.remove()
        super.onDestroy()
    }
}
