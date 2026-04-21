package com.formykids.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.formykids.App
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class BillingManager(context: Context, private val serverUrl: String) : PurchasesUpdatedListener {

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    var onPurchaseSuccess: (() -> Unit)? = null
    var onPurchaseError: ((String) -> Unit)? = null

    fun connect(onReady: () -> Unit) {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) onReady()
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    suspend fun queryAndLaunch(activity: Activity) {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            )).build()
        val result = client.queryProductDetails(params)
        val details = result.productDetailsList?.firstOrNull() ?: run {
            onPurchaseError?.invoke("상품 정보를 불러올 수 없습니다"); return
        }
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .setOfferToken(offerToken)
                    .build()
            )).build()
        client.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) {
            onPurchaseError?.invoke("결제 실패 (${result.responseCode})")
            return
        }
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                CoroutineScope(Dispatchers.IO).launch {
                    verifyWithServer(purchase.purchaseToken)
                    client.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                    ) {}
                }
            }
        }
    }

    private suspend fun verifyWithServer(purchaseToken: String) {
        val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return
        val body = JSONObject(mapOf("idToken" to idToken, "purchaseToken" to purchaseToken))
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$serverUrl/verify-purchase").post(body).build()
        runCatching { OkHttpClient().newCall(request).execute() }.onSuccess {
            if (it.isSuccessful) onPurchaseSuccess?.invoke()
            else onPurchaseError?.invoke("서버 검증 실패")
        }
    }

    fun release() = client.endConnection()

    companion object {
        const val PRODUCT_ID = "premium_monthly"

        fun isSubscriptionActive(expiresAtMs: Long?): Boolean {
            if (expiresAtMs == null) return false
            return expiresAtMs > System.currentTimeMillis()
        }
    }
}
