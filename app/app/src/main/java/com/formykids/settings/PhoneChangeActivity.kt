package com.formykids.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.formykids.databinding.ActivityPhoneChangeBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class PhoneChangeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhoneChangeBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener {
            val phone = binding.etPhone.text.toString().filter { it.isDigit() }
            if (phone.isEmpty()) {
                Toast.makeText(this, "전화번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnSendCode.isEnabled = false
            binding.btnSendCode.text = "전송 중..."
            sendVerificationCode("+82${phone.removePrefix("0")}")
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            val vid = verificationId ?: return@setOnClickListener
            val credential = PhoneAuthProvider.getCredential(vid, code)
            updatePhone(credential)
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    updatePhone(credential)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    binding.tilPhone.visibility = View.VISIBLE
                    binding.btnSendCode.visibility = View.VISIBLE
                    binding.btnSendCode.isEnabled = true
                    binding.btnSendCode.text = getString(com.formykids.R.string.send_code)
                    Toast.makeText(this@PhoneChangeActivity, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vid
                    binding.tilPhone.visibility = View.GONE
                    binding.btnSendCode.visibility = View.GONE
                    binding.layoutCodeEntry.visibility = View.VISIBLE
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun updatePhone(credential: PhoneAuthCredential) {
        Firebase.auth.currentUser?.updatePhoneNumber(credential)
            ?.addOnSuccessListener {
                Toast.makeText(this, getString(com.formykids.R.string.change_phone_success), Toast.LENGTH_SHORT).show()
                finish()
            }
            ?.addOnFailureListener {
                Toast.makeText(this, "변경 실패: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
