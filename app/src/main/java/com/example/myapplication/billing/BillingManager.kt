// ЗАМЕНИТЕ файл app/src/main/java/com/example/myapplication/billing/BillingManager.kt на:

package com.example.myapplication.billing

import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(context: Context) {
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult

    data class PurchaseResult(
        val success: Boolean,
        val message: String,
        val amount: Double = 0.0
    )

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    for (purchase in purchaseList) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            _purchaseResult.value = PurchaseResult(
                                success = true,
                                message = "Purchase successful",
                                amount = getPurchaseAmount(purchase)
                            )
                            // Подтверждаем покупку
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResult.value = PurchaseResult(
                    success = false,
                    message = "User canceled purchase"
                )
            }
            else -> {
                _purchaseResult.value = PurchaseResult(
                    success = false,
                    message = "Purchase failed: ${billingResult.debugMessage}"
                )
            }
        }
    }

    val billingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryPurchases()
                }
            }
            override fun onBillingServiceDisconnected() {
                // Переподключение обрабатывается автоматически
            }
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

    fun launchBillingFlow(activity: android.app.Activity, productId: String) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(getProductDetails(productId))
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Покупка подтверждена
            }
        }
    }

    private fun getProductDetails(productId: String): ProductDetails {
        // В реальной реализации нужно получить ProductDetails через queryProductDetailsAsync
        // Здесь упрощенная версия
        throw NotImplementedError("Product details should be fetched from BillingClient")
    }

    private fun getPurchaseAmount(purchase: Purchase): Double {
        // Парсинг суммы из purchase.originalJson
        // В реальности сумма должна быть получена из ProductDetails
        return 0.0
    }
}