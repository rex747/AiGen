package com.example.myapplication.billing

import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(context: Context) {
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        // Обработка завершённых покупок
    }

    val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts() // Включает поддержку для разовых покупок (INAPP)
                .build()
        )
        .enableAutoServiceReconnection()   // новая фича библиотеки 8.0.0
        .build()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // готов к работе
                }
            }
            override fun onBillingServiceDisconnected() { /* переподключение */ }
        })
    }

    fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremium.value = purchaseList.any { it.products.contains("premium_skill_pack") }
            }
        }
    }

    // Дополнительные методы: launchBillingFlow, handlePurchase и т.д.
}