package com.nurirppan.development.paywall

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType
import com.nurirppan.development.R
import com.nurirppan.development.databinding.ActivitySingleInAppSubscriptionsBinding
import java.io.IOException
import java.util.*

class SingleInAppSubscriptionsAct : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var binding: ActivitySingleInAppSubscriptionsBinding

    private var billingClient: BillingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySingleInAppSubscriptionsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Establish connection to billing client
        //check subscription status from google play store cache
        //to check if item is already Subscribed or subscription is not renewed and cancelled
        billingClient =
            BillingClient.newBuilder(this).enablePendingPurchases().setListener(this).build()
        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val queryPurchase = billingClient!!.queryPurchases(SkuType.SUBS)
                    val queryPurchases = queryPurchase.purchasesList
                    if (queryPurchases != null && queryPurchases.size > 0) {
                        handlePurchases(queryPurchases)
                    } else {
                        saveSubscribeValueToPref(false)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                Toast.makeText(applicationContext, "Service Disconnected", Toast.LENGTH_SHORT)
                    .show()
            }
        })

        //item subscribed
        if (subscribeValueFromPref) {
            binding.subscribe!!.visibility = View.GONE
            binding.premiumContent!!.visibility = View.VISIBLE
            binding.subscriptionStatus!!.text = "Subscription Status : Subscribed"
        } else {
            binding.premiumContent!!.visibility = View.GONE
            binding.subscribe!!.visibility = View.VISIBLE
            binding.subscriptionStatus!!.text = "Subscription Status : Not Subscribed"
        }
        setOnClickListerners()
    }

    private val preferenceObject: SharedPreferences
        get() = applicationContext.getSharedPreferences(PREF_FILE, 0)

    private val preferenceEditObject: SharedPreferences.Editor
        get() {
            val pref = applicationContext.getSharedPreferences(PREF_FILE, 0)
            return pref.edit()
        }

    private val subscribeValueFromPref: Boolean
        get() = preferenceObject.getBoolean(SUBSCRIBE_KEY, false)

    private fun saveSubscribeValueToPref(value: Boolean) {
        preferenceEditObject.putBoolean(SUBSCRIBE_KEY, value).commit()
    }

    //initiate purchase on button click
    fun setOnClickListerners() {
        binding.subscribe.setOnClickListener {
            //check if service is already connected
            if (billingClient!!.isReady) {
                initiatePurchase()
            } else {
                billingClient =
                    BillingClient.newBuilder(this).enablePendingPurchases().setListener(this)
                        .build()
                billingClient!!.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            initiatePurchase()
                        } else {
                            Toast.makeText(
                                applicationContext,
                                "Error " + billingResult.debugMessage,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                        Toast.makeText(
                            applicationContext,
                            "Service Disconnected ",
                            Toast.LENGTH_SHORT
                        )
                            .show()
                    }
                })
            }
        }

    }

    private fun initiatePurchase() {
        val skuList: MutableList<String> = ArrayList()
        skuList.add(ITEM_SKU_SUBSCRIBE)
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(SkuType.SUBS)
        val billingResult =
            billingClient!!.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS)
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            billingClient!!.querySkuDetailsAsync(
                params.build()
            ) { billingResult, skuDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    if (skuDetailsList != null && skuDetailsList.size > 0) {
                        val flowParams = BillingFlowParams.newBuilder()
                            .setSkuDetails(skuDetailsList[0])
                            .build()
                        billingClient!!.launchBillingFlow(this, flowParams)
                    } else {
                        //try to add subscription item "sub_example" in google play console
                        Toast.makeText(applicationContext, "Item not Found", Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        " Error " + billingResult.debugMessage, Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                applicationContext,
                "Sorry Subscription not Supported. Please Update Play Store", Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        //if item subscribed
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            val queryAlreadyPurchasesResult = billingClient!!.queryPurchases(SkuType.SUBS)
            val alreadyPurchases = queryAlreadyPurchasesResult.purchasesList
            alreadyPurchases?.let { handlePurchases(it) }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Toast.makeText(applicationContext, "Purchase Canceled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                applicationContext,
                "Error " + billingResult.debugMessage,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun handlePurchases(purchases: List<Purchase>) {
        for (purchase in purchases) {
            //if item is purchased
            if (ITEM_SKU_SUBSCRIBE == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                    // Invalid purchase
                    // show error to user
                    Toast.makeText(
                        applicationContext,
                        "Error : invalid Purchase",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
                // else purchase is valid
                //if item is purchased and not acknowledged
                if (!purchase.isAcknowledged) {
                    val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient!!.acknowledgePurchase(acknowledgePurchaseParams, ackPurchase)
                } else {
                    // Grant entitlement to the user on item purchase
                    // restart activity
                    if (!subscribeValueFromPref) {
                        saveSubscribeValueToPref(true)
                        Toast.makeText(applicationContext, "Item Purchased", Toast.LENGTH_SHORT)
                            .show()
                        recreate()
                    }
                }
            } else if (ITEM_SKU_SUBSCRIBE == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                Toast.makeText(
                    applicationContext,
                    "Purchase is Pending. Please complete Transaction", Toast.LENGTH_SHORT
                ).show()
            } else if (ITEM_SKU_SUBSCRIBE == purchase.sku && purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                saveSubscribeValueToPref(false)
                binding.premiumContent!!.visibility = View.GONE
                binding.subscribe!!.visibility = View.VISIBLE
                binding.subscriptionStatus!!.text = "Subscription Status : Not Subscribed"
                Toast.makeText(applicationContext, "Purchase Status Unknown", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    var ackPurchase = AcknowledgePurchaseResponseListener { billingResult ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            //if purchase is acknowledged
            // Grant entitlement to the user. and restart activity
            saveSubscribeValueToPref(true)
            recreate()
        }
    }

    /**
     * Verifies that the purchase was signed correctly for this developer's public key.
     *
     * Note: It's strongly recommended to perform such check on your backend since hackers can
     * replace this method with "constant true" if they decompile/rebuild your app.
     *
     */
    private fun verifyValidSignature(signedData: String, signature: String): Boolean {
        return try {
            // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
            val base64Key = getString(R.string.rsa_public_key)
            SingleInAppSubscriptionsSecurity.verifyPurchase(base64Key, signedData, signature)
        } catch (e: IOException) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (billingClient != null) {
            billingClient!!.endConnection()
        }
    }

    companion object {
        const val PREF_FILE = "MyPref"
        const val SUBSCRIBE_KEY = "SingleInAppSubscriptionsKey"
        const val ITEM_SKU_SUBSCRIBE = "subs_single_id"
    }
}