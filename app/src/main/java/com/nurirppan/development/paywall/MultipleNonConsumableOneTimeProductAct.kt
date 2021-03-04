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
import com.nurirppan.development.R
import com.nurirppan.development.databinding.ActivityMultipleNonConsumableOneTimeProductBinding
import com.nurirppan.development.databinding.ActivitySingleConsumableOneTimeProductBinding
import java.io.IOException
import java.util.*

class MultipleNonConsumableOneTimeProductAct : AppCompatActivity(), PurchasesUpdatedListener {

    private lateinit var binding: ActivityMultipleNonConsumableOneTimeProductBinding

    var arrayAdapter: ArrayAdapter<String>? = null
    private var billingClient: BillingClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("Act__", "MultipleNonConsumableOneTimeProductAct")

        binding = ActivityMultipleNonConsumableOneTimeProductBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

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

                    //check which items are in purchase list and which are not in purchase list
                    //if items that are found add them to purchaseFound
                    //check status of found items and save values to preference
                    //item which are not found simply save false values to their preference
                    //indexOf return index of item in purchase list from 0-2 (because we have 3 items) else returns -1 if not found
                    val purchaseFound = ArrayList<Int>()
                    if (queryPurchases != null && queryPurchases.size > 0) {
                        //check item in purchase list
                        for (p in queryPurchases) {
                            val index = purchaseItemIDs.indexOf(p.sku)
                            //if purchase found
                            if (index > -1) {
                                purchaseFound.add(index)
                                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                                    savePurchaseItemValueToPref(purchaseItemIDs[index], true)
                                } else {
                                    savePurchaseItemValueToPref(purchaseItemIDs[index], false)
                                }
                            }
                        }
                        //items that are not found in purchase list mark false
                        //indexOf returns -1 when item is not in foundlist
                        for (i in purchaseItemIDs.indices) {
                            if (purchaseFound.indexOf(i) == -1) {
                                savePurchaseItemValueToPref(purchaseItemIDs[i], false)
                            }
                        }
                    } else {
                        for (purchaseItem in purchaseItemIDs) {
                            savePurchaseItemValueToPref(purchaseItem, false)
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {}
        })
        arrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, purchaseItemDisplay)
        binding.listview!!.adapter = arrayAdapter
        notifyList()
        binding.listview!!.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            if (getPurchaseItemValueFromPref(purchaseItemIDs[position])) {
                Toast.makeText(
                    applicationContext,
                    purchaseItemIDs[position] + " is Already Purchased",
                    Toast.LENGTH_SHORT
                ).show()
                //selected item is already purchased
                return@OnItemClickListener
            }
            //initiate purchase on selected product item click
            //check if service is already connected
            if (billingClient!!.isReady) {
                initiatePurchase(purchaseItemIDs[position])
            } else {
                billingClient = BillingClient.newBuilder(this).enablePendingPurchases()
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
            purchaseItemDisplay.add(
                "Purchase Status of " + p + " = " + getPurchaseItemValueFromPref(
                    p
                )
            )
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

    private fun getPurchaseItemValueFromPref(PURCHASE_KEY: String): Boolean {
        return preferenceObject.getBoolean(PURCHASE_KEY, false)
    }

    private fun savePurchaseItemValueToPref(PURCHASE_KEY: String, value: Boolean) {
        preferenceEditObject.putBoolean(PURCHASE_KEY, value).commit()
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
                    //try to add item/product id "p1" "p2" "p3" inside managed product in google play console
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
                    //if item is purchased and not  Acknowledged
                    if (!purchase.isAcknowledged) {
                        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient!!.acknowledgePurchase(
                            acknowledgePurchaseParams
                        ) { billingResult ->
                            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                //if purchase is acknowledged
                                //then saved value in preference
                                savePurchaseItemValueToPref(purchaseItemIDs[index], true)
                                Toast.makeText(
                                    applicationContext,
                                    purchaseItemIDs[index] + " Item Purchased",
                                    Toast.LENGTH_SHORT
                                ).show()
                                notifyList()
                            }
                        }
                    } else {
                        // Grant entitlement to the user on item purchase
                        if (!getPurchaseItemValueFromPref(purchaseItemIDs[index])) {
                            savePurchaseItemValueToPref(purchaseItemIDs[index], true)
                            Toast.makeText(
                                applicationContext,
                                purchaseItemIDs[index] + " Item Purchased.",
                                Toast.LENGTH_SHORT
                            ).show()
                            notifyList()
                        }
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    Toast.makeText(
                        applicationContext,
                        purchaseItemIDs[index] + " Purchase is Pending. Please complete Transaction",
                        Toast.LENGTH_SHORT
                    ).show()
                } else if (purchase.purchaseState == Purchase.PurchaseState.UNSPECIFIED_STATE) {
                    //mark purchase false in case of UNSPECIFIED_STATE
                    savePurchaseItemValueToPref(purchaseItemIDs[index], false)
                    Toast.makeText(
                        applicationContext,
                        purchaseItemIDs[index] + " Purchase Status Unknown",
                        Toast.LENGTH_SHORT
                    ).show()
                    notifyList()
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
            MultipleNonConsumableOneTimeProductSecurity.verifyPurchase(
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
                add("p1")
                add("p2")
                add("p3")
            }
        }
        private val purchaseItemDisplay = ArrayList<String>()
    }
}