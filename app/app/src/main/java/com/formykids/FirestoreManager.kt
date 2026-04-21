package com.formykids

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object FirestoreManager {
    private val db get() = Firebase.firestore
    private val auth get() = Firebase.auth

    suspend fun getCurrentUser(): Map<String, Any?>? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("users").document(uid).get().await().data
    }

    suspend fun updateFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    suspend fun getSubscription(): Map<String, Any?>? {
        val uid = auth.currentUser?.uid ?: return null
        return db.collection("subscriptions").document(uid).get().await().data
    }

    suspend fun isPremium(): Boolean {
        val sub = getSubscription() ?: return false
        val plan = sub["plan"] as? String ?: return false
        val expiresAt = sub["expiresAt"] as? Long ?: return false
        return plan == "premium" && expiresAt > System.currentTimeMillis()
    }

    suspend fun getAlerts(familyId: String): List<Map<String, Any?>> {
        return db.collection("alerts")
            .whereEqualTo("familyId", familyId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(50)
            .get().await()
            .documents.map { it.data ?: emptyMap() }
    }
}
