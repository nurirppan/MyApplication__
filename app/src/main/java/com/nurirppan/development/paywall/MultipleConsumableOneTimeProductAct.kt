package com.nurirppan.development.paywall

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingClient.SkuType
import java.io.IOException
import java.util.*
import com.nurirppan.development.R

class MultipleConsumableOneTimeProductAct : AppCompatActivity(), PurchasesUpdatedListener {
    var arrayAdapter: ArrayAdapter<String?>? = null
    var listView: ListView? = null
    private var billingClient: BillingClient? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("Act__", "MultipleConsumableOneTimeProductAct")

        setContentView(R.layout.activity_multiple_consumable_one_time_product)
        listView = findViewById<View>(R.id.listview) as ListView

        // Establish connection to billing client
        //check purchase status from google play store cache on every app start
        billingClient = BillingClient.newBuilder(this)
            .enablePendingPurchases().setListener(this).build()
        billingClient!!.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val queryPurchase = billingClient!!.queryPurchases(SkuType.INAPP)
                    val queryPurchases = queryPurchase.purchasesList
                    if (queryPurchases != null && queryPurchases.size > 0) {
                        handlePurchases(queryPurchases)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, purchaseItemDisplay)
        listView!!.adapter = arrayAdapter
        notifyList()
        listView!!.onItemClickListener =
            OnItemClickListener { parent, view, position, id -> //initiate purchase on selected consume item click
                //check if service is already connected
                if (billingClient!!.isReady) {
                    initiatePurchase(purchaseItemIDs[position])
                } else {
                    billingClient =
                        BillingClient.newBuilder(this).enablePendingPurchases()
                            .setListener(this).build()
                    billingClient!!.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                initiatePurchase(purchaseItemIDs[position])
                            } else {
                                Toast.makeText(
                                    applicationContext,
                                    "Error " + billingResult.debugMessage,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onBillingServiceDisconnected() {}
                    })
                }
            }
    }

    private fun notifyList() {
        purchaseItemDisplay.clear()
        for (p in purchaseItemIDs) {
            purchaseItemDisplay.add(p + " consumed " + getPurchaseCountValueFromPref(p) + " time(s)")
        }
        arrayAdapter!!.notifyDataSetChanged()
    }

    private val preferenceObject: SharedPreferences
        private get() = applicationContext.getSharedPreferences(PREF_FILE, 0)
    private val preferenceEditObject: Editor
        private get() {
            val pref = applicationContext.getSharedPreferences(PREF_FILE, 0)
            return pref.edit()
        }

    private fun getPurchaseCountValueFromPref(PURCHASE_KEY: String): Int {
        return preferenceObject.getInt(PURCHASE_KEY, 0)
    }

    private fun savePurchaseCountValueToPref(PURCHASE_KEY: String, value: Int) {
        preferenceEditObject.putInt(PURCHASE_KEY, value).commit()
    }

    private fun initiatePurchase(PRODUCT_ID: String) {
        val skuList: MutableList<String> = ArrayList()
        skuList.add(PRODUCT_ID)
        val params = SkuDetailsParams.newBuilder()
        params.setSkusList(skuList).setType(SkuType.INAPP)
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
                    //try to add item/product id "c1" "c2" "c3" inside managed product in google play console
                    Toast.makeText(
                        applicationContext,
                        "Purchase Item $PRODUCT_ID not Found",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                Toast.makeText(
                    applicationContext,
                    " Error " + billingResult.debugMessage, Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        //if item newly purchased
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            val queryAlreadyPurchasesResult = billingClient!!.queryPurchases(SkuType.INAPP)
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
            val index = purchaseItemIDs.indexOf(purchase.sku)
            //purchase found
            if (index > -1) {

                //if item is purchased
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    if (!verifyValidSignature(purchase.originalJson, purchase.signature)) {
                        // Invalid purchase
                        // show error to user
                        Toast.makeText(
                            applicationContext,
                            "Error : Invalid Purchase",
                            Toast.LENGTH_SHORT
                        ).show()
                        continue  //skip current iteration only because other items in purchase list must be checked if present
                    }
                    // else purchase is valid
                    //if item is purchased and not consumed
                    if (!purchase.isAcknowledged) {
                        val consumeParams = ConsumeParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient!!.consumeAsync(consumeParams) { billingResult, purchaseToken ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                val consumeCountValue =
                                    getPurchaseCountValueFromPref(purchaseItemIDs[index]) + 1
                                savePurchaseCountValueToPref(
                                    purchaseItemIDs[index],
                                    consumeCountValue
                                )
                                Toast.makeText(
                                    applicationContext,
                                    "Item " + purchaseItemIDs[index] + "Consumed",
                                    Toast.LENGTH_SHORT
                                ).show()
                                notifyList()
                            }
                        }
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    Toast.makeText(
                        applicationContext,
                        purchaseItemIDs[index] + " Purchase is Pending. Please complete Transaction",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    Toast.makeText(
                        applicationContext,
                        purchaseItemIDs[index] + " Purchase Status Unknown",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
            //for old playconsole
            // To get key go to Developer Console > Select your app > Development Tools > Services & APIs.
            //for new play console
            //To get key go to Developer Console > Select your app > Monetize > Monetization setup
            val base64Key = getString(R.string.rsa_public_key)
            MultipleConsumableOneTimeProductActSecurity.verifyPurchase(
                base64Key,
                signedData,
                signature
            )
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

        //note add unique product ids
        //use same id for preference key
        private val purchaseItemIDs: ArrayList<String> = object : ArrayList<String>() {
            init {
                add("c1_id")
                add("c2_id")
                add("c3_id")
            }
        }
        private val purchaseItemDisplay: ArrayList<String?> = ArrayList<String?>()
    }
}