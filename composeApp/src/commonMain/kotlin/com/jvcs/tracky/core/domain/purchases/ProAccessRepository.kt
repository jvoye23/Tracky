package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

interface ProAccessRepository {

    /** Whether the current customer holds the active Pro entitlement. */
    suspend fun hasProAccess(): Result<Boolean, DataError.Remote>

    /** The packages of the current offering. Empty when no offering is current or it has none. */
    suspend fun getOfferings(): Result<List<ProPackage>, DataError.Remote>

    /** Buys [proPackage]. Success carries whether the Pro entitlement is active afterwards. */
    suspend fun purchase(proPackage: ProPackage): Result<Boolean, PurchaseError>

    /**
     * Restores the purchases made with this store account, e.g. on a new device. Success carries
     * whether the Pro entitlement is active afterwards.
     *
     * Call this only in response to a user action, such as tapping a Restore button: it may show
     * OS-level sign-in prompts. To restore without the user asking, use [syncPurchases].
     */
    suspend fun restorePurchases(): Result<Boolean, DataError.Remote>

    /**
     * Syncs the store's purchases to RevenueCat without user interaction, and never shows OS-level
     * sign-in prompts. Use this, not [restorePurchases], when restoring programmatically. Success
     * carries whether the Pro entitlement is active afterwards.
     */
    suspend fun syncPurchases(): Result<Boolean, DataError.Remote>

    /**
     * Switches the store customer to [appUserId]. Switching between two known ids does not merge
     * them. A purchase made on the previous id stays there until [syncPurchases] moves the receipt.
     *
     * There is deliberately no logout. Logging out would fall back to an anonymous customer, and the
     * server cannot use that customer's id; see `AppUserIdStore`.
     */
    suspend fun identify(appUserId: String): EmptyResult<DataError.Remote>
}
