package com.example.myapplication.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(context: Context) {

    // ========================================================================
    // Sealed class для результата покупки (внутри класса)
    // ========================================================================
    sealed class PurchaseResult {
        data class Success(val purchaseToken: String) : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
        object Cancelled : PurchaseResult()
        object Pending : PurchaseResult()
    }

    // ========================================================================
    // Состояния
    // ========================================================================
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _purchaseResult = MutableStateFlow<PurchaseResult?>(null)
    val purchaseResult: StateFlow<PurchaseResult?> = _purchaseResult.asStateFlow()

    // Кэш деталей продуктов (заполняется через queryProductDetailsAsync)
    private var productDetailsMap = mutableMapOf<String, ProductDetails>()

    // ========================================================================
    // Listener для обработки результатов покупок от Google Play Billing
    // ========================================================================
    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    for (purchase in purchaseList) {
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            // Публикуем результат с purchaseToken
                            _purchaseResult.value = PurchaseResult.Success(purchase.purchaseToken)
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseResult.value = PurchaseResult.Cancelled
            }
            else -> {
                _purchaseResult.value = PurchaseResult.Error(
                    billingResult.debugMessage ?: "Purchase failed with code: ${billingResult.responseCode}"
                )
            }
        }
    }

    // ========================================================================
    // BillingClient
    // ========================================================================
    val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    // ========================================================================
    // Установка соединения с Google Play Billing
    // ========================================================================
    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    // Загружаем детали продуктов для подписок
                    queryProductDetails()
                    queryPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Переподключение обрабатывается автоматически через enableAutoServiceReconnection()
            }
        })
    }

    // ========================================================================
    // Загрузка деталей продуктов из Google Play Console
    // ========================================================================
    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("subscription_monthly")
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId("subscription_yearly")
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (details in productDetailsList) {
                    productDetailsMap[details.productId] = details
                }
            }
        }
    }

    // ========================================================================
    // Запрос существующих покупок
    // ========================================================================
    fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { billingResult, purchaseList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _isPremium.value = purchaseList.any {
                    it.products.contains("premium_skill_pack") ||
                            it.products.contains("subscription_monthly") ||
                            it.products.contains("subscription_yearly")
                }
            }
        }
    }

    // ========================================================================
    // Запуск платежного потока (2 параметра: activity и productId)
    // ========================================================================
    fun launchBillingFlow(activity: Activity, productId: String) {
        // Устанавливаем статус "в процессе" перед запуском
        _purchaseResult.value = PurchaseResult.Pending

        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            _purchaseResult.value = PurchaseResult.Error("Product details not found for: $productId")
            return
        }

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    // ========================================================================
    // Инициирование платежа по типу подписки (обёртка над launchBillingFlow)
    // ========================================================================
    fun initiatePayment(activity: Activity, planType: String) {
        val productId = when (planType) {
            "monthly" -> "subscription_monthly"
            "yearly" -> "subscription_yearly"
            else -> "subscription_monthly"
        }

        launchBillingFlow(activity, productId)
    }

    // ========================================================================
    // Подтверждение/потребление покупки
    // ========================================================================
    private fun acknowledgePurchase(purchase: Purchase) {
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(consumeParams) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Покупка подтверждена/потреблена успешно
            }
        }
    }
}