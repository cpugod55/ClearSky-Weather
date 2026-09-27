package com.clearsky.weather

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

data class SupportProduct(
    val id: String,
    val price: String
)

class SupportBillingManager(
    private val activity: Activity,
    private val onProducts: (List<SupportProduct>) -> Unit,
    private val onSupportRecognized: () -> Unit
) : PurchasesUpdatedListener {

    companion object {
        val PRODUCT_IDS = listOf("support_1", "support_3", "support_5")
    }

    private val productDetails = mutableMapOf<String, ProductDetails>()

    private val billingClient = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProducts()
                    restoreSupportStatus()
                }
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun close() {
        billingClient.endConnection()
    }

    fun purchase(productId: String) {
        val details = productDetails[productId] ?: return
        val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)

        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?.let(paramsBuilder::setOfferToken)

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(paramsBuilder.build()))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(
        result: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            purchases.orEmpty().forEach(::handlePurchase)
        }
    }

    private fun queryProducts() {
        val products = PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                onProducts(emptyList())
                return@queryProductDetailsAsync
            }

            productDetails.clear()
            queryResult.productDetailsList.forEach { details ->
                productDetails[details.productId] = details
            }

            onProducts(
                PRODUCT_IDS.mapNotNull { id ->
                    productDetails[id]?.let { details ->
                        SupportProduct(id, formattedPrice(details))
                    }
                }
            )
        }
    }

    private fun restoreSupportStatus() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                purchases.forEach(::handlePurchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (purchase.products.none(PRODUCT_IDS::contains)) return

        if (purchase.isAcknowledged) {
            onSupportRecognized()
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onSupportRecognized()
            }
        }
    }

    private fun formattedPrice(details: ProductDetails): String {
        return details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.formattedPrice
            ?: details.oneTimePurchaseOfferDetails?.formattedPrice
            ?: "Support"
    }
}
