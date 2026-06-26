package com.zero.crm

import android.app.Application
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.LogLevel

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize RevenueCat for commercial release
        try {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(this, "test_HziDYjISItTxFRVNtZeANqhPlTA").build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
