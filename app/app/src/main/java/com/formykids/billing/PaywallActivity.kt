package com.formykids.billing

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.R
import com.formykids.databinding.ActivityPaywallBinding
import kotlinx.coroutines.launch

class PaywallActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPaywallBinding
    private lateinit var billing: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        listOf(
            binding.row1, binding.row2, binding.row3, binding.row4
        ).zip(listOf(
            getString(R.string.feature_unlimited_streaming),
            getString(R.string.feature_ai_detection),
            getString(R.string.feature_alert_history),
            getString(R.string.feature_audio_snippet)
        )).forEach { (row, text) ->
            row.tvFeature.text = text
        }

        val prefs = getSharedPreferences(App.PREF_NAME, MODE_PRIVATE)
        val serverUrl = prefs.getString(App.PREF_SERVER_URL, App.DEFAULT_SERVER_URL)!!

        billing = BillingManager(this, serverUrl)
        billing.onPurchaseSuccess = {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.subscription_started), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }
        billing.onPurchaseError = { msg ->
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }

        billing.connect { runOnUiThread { binding.btnSubscribe.isEnabled = true } }

        binding.btnSubscribe.setOnClickListener {
            lifecycleScope.launch { billing.queryAndLaunch(this@PaywallActivity) }
        }

        binding.btnClose.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        billing.release()
        super.onDestroy()
    }
}
