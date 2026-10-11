package com.jvcs.tracky.core.data.purchases

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.purchases.ProAccessRepository
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.PurchaseError
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.result.awaitCustomerInfoResult
import com.revenuecat.purchases.kmp.result.awaitLogInResult
import com.revenuecat.purchases.kmp.result.awaitOfferingsResult
import com.revenuecat.purchases.kmp.result.awaitPurchaseResult
import com.revenuecat.purchases.kmp.result.awaitRestoreResult
import com.revenuecat.purchases.kmp.result.awaitSyncPurchasesResult

/** Reads Pro access from RevenueCat. Every call waits until [RevenueCatConfigurator] has configured it. */
class RevenueCatProAccessRepository(private val configurator: RevenueCatConfigurator) : ProAccessRepository {

    private suspend fun purchases(): Purchases {
        configurator.awaitConfigured()
        return Purchases.sharedInstance
    }

    override suspend fun hasProAccess(): Result<Boolean, DataError.Remote> =
        purchases().awaitCustomerInfoResult().fold(
            onSuccess = { customerInfo ->
                Result.Success(customerInfo.entitlements.active.containsKey(PRO_ENTITLEMENT))
            },
            onFailure = { throwable ->
                Logger.withTag(TAG).w(throwable) { "Reading customer info failed" }
                Result.Error(throwable.toPurchasesDataError())
            },
        )

    override suspend fun getOfferings(): Result<List<ProPackage>, DataError.Remote> =
        purchases().awaitOfferingsResult().fold(
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages.orEmpty()
                Result.Success(packages.map { it.toProPackage() })
            },
            onFailure = { throwable ->
                Logger.withTag(TAG).w(throwable) { "Reading offerings failed" }
                Result.Error(throwable.toPurchasesDataError())
            },
        )

    override suspend fun purchase(proPackage: ProPackage): Result<Boolean, PurchaseError> {
        // ProPackage carries no RevenueCat object, so the Package is looked up again. RevenueCat
        // caches offerings, so this does not go to the network a second time.
        val offerings =
            purchases().awaitOfferingsResult().getOrElse { throwable ->
                Logger.withTag(TAG).w(throwable) { "Reading offerings for a purchase failed" }
                return Result.Error(PurchaseError.Remote(throwable.toPurchasesDataError()))
            }
        val storePackage =
            offerings.current
                ?.availablePackages
                ?.firstOrNull { it.identifier == proPackage.identifier }
                ?: return Result.Error(PurchaseError.PackageNotFound)

        return purchases().awaitPurchaseResult(storePackage).fold(
            onSuccess = { purchase ->
                Result.Success(
                    purchase.customerInfo.entitlements.active
                        .containsKey(PRO_ENTITLEMENT),
                )
            },
            onFailure = { throwable ->
                val error = throwable.toPurchaseError()
                // A cancel is the user changing their mind, not something to look into.
                if (error != PurchaseError.Cancelled) {
                    Logger.withTag(TAG).w(throwable) { "Purchasing ${proPackage.identifier} failed" }
                }
                Result.Error(error)
            },
        )
    }

    override suspend fun restorePurchases(): Result<Boolean, DataError.Remote> =
        purchases().awaitRestoreResult().fold(
            onSuccess = { customerInfo ->
                Result.Success(customerInfo.entitlements.active.containsKey(PRO_ENTITLEMENT))
            },
            onFailure = { throwable ->
                Logger.withTag(TAG).w(throwable) { "Restoring purchases failed" }
                Result.Error(throwable.toPurchasesDataError())
            },
        )

    override suspend fun syncPurchases(): Result<Boolean, DataError.Remote> =
        purchases().awaitSyncPurchasesResult().fold(
            onSuccess = { customerInfo ->
                Result.Success(customerInfo.entitlements.active.containsKey(PRO_ENTITLEMENT))
            },
            onFailure = { throwable ->
                Logger.withTag(TAG).w(throwable) { "Syncing purchases failed" }
                Result.Error(throwable.toPurchasesDataError())
            },
        )

    override suspend fun identify(appUserId: String): EmptyResult<DataError.Remote> =
        purchases().awaitLogInResult(appUserId).fold(
            onSuccess = { Result.Success(Unit) },
            onFailure = { throwable ->
                Logger.withTag(TAG).w(throwable) { "Identifying the store customer failed" }
                Result.Error(throwable.toPurchasesDataError())
            },
        )

    private companion object {
        const val TAG = "RevenueCatProAccessRepository"
        const val PRO_ENTITLEMENT = "project_tracker_tracky_pro"
    }
}
