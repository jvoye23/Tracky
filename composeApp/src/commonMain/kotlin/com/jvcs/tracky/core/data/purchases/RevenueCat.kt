package com.jvcs.tracky.core.data.purchases

import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.configure

/** RevenueCat's public SDK key. Per platform, because App Store and Play keys differ. */
expect val revenueCatApiKey: String

/**
 * Configures the Purchases singleton for [appUserId], the id from `AppUserIdStore`. Called once, by
 * [RevenueCatConfigurator]. On Android the SDK finds its Context through App Startup, so none is
 * passed here.
 *
 * Always pass the id. Without it, RevenueCat starts an anonymous customer, and the server cannot tie
 * that customer's purchase to the account the user registers later.
 */
fun configureRevenueCat(appUserId: String) {
    Purchases.configure(apiKey = revenueCatApiKey) {
        this.appUserId = appUserId
    }
}
