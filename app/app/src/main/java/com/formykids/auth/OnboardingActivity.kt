package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.formykids.databinding.ActivityOnboardingBinding
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            sendVerificationCode("+82${phone.removePrefix("0")}")
        }

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text.toString().trim()
            val vid = verificationId ?: return@setOnClickListener
            val credential = PhoneAuthProvider.getCredential(vid, code)
            signInWithCredential(credential)
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }
                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@OnboardingActivity, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vid
                    binding.layoutCodeEntry.visibility = View.VISIBLE
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        Firebase.auth.signInWithCredential(credential).addOnSuccessListener {
            startActivity(Intent(this, RoleSelectActivity::class.java))
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "로그인 실패: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
