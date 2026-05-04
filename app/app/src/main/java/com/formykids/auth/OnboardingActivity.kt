package com.formykids.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.formykids.App
import com.formykids.FirestoreManager
import com.formykids.child.ChildActivity
import com.formykids.databinding.ActivityOnboardingBinding
import com.formykids.parent.ParentActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
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
                    binding.tilPhone.visibility = View.VISIBLE
                    binding.btnSendCode.visibility = View.VISIBLE
                    binding.btnSendCode.isEnabled = true
                    binding.btnSendCode.text = getString(com.formykids.R.string.send_code)
                    Toast.makeText(this@OnboardingActivity, "인증 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
                override fun onCodeSent(vid: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = vid
                    binding.tilPhone.visibility = View.GONE
                    binding.btnSendCode.visibility = View.GONE
                    binding.layoutCodeEntry.visibility = View.VISIBLE
                }
            })
            .build()
        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: android.content.ActivityNotFoundException) {
            binding.tilPhone.visibility = View.VISIBLE
            binding.btnSendCode.visibility = View.VISIBLE
            binding.btnSendCode.isEnabled = true
            binding.btnSendCode.text = getString(com.formykids.R.string.send_code)
            Toast.makeText(this, "인증을 위해 Chrome 브라우저가 필요합니다.\n아이 폰에 Chrome을 설치한 후 다시 시도해주세요.", Toast.LENGTH_LONG).show()
        }
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        Firebase.auth.signInWithCredential(credential).addOnSuccessListener {
            lifecycleScope.launch {
                try {
                    val userData = FirestoreManager.getCurrentUser()
                    val role = userData?.get("role") as? String
                    val familyId = userData?.get("familyId") as? String
                    val target = when {
                        role == App.ROLE_PARENT && !familyId.isNullOrEmpty() -> ParentActivity::class.java
                        role == App.ROLE_CHILD && !familyId.isNullOrEmpty() -> ChildActivity::class.java
                        else -> RoleSelectActivity::class.java
                    }
                    startActivity(Intent(this@OnboardingActivity, target))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@OnboardingActivity, "네트워크 오류. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
                }
            }
        }.addOnFailureListener {
            Toast.makeText(this, "로그인 실패: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }
}
