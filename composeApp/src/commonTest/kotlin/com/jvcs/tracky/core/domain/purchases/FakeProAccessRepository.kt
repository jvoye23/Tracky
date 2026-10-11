package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result

/** Answers with the configured results, which a test can swap between calls. */
class FakeProAccessRepository(
    var result: Result<Boolean, DataError.Remote> = Result.Success(false),
    var offeringsResult: Result<List<ProPackage>, DataError.Remote> = Result.Success(emptyList()),
    var purchaseResult: Result<Boolean, PurchaseError> = Result.Success(true),
    var restoreResult: Result<Boolean, DataError.Remote> = Result.Success(false),
    var syncResult: Result<Boolean, DataError.Remote> = Result.Success(false),
) : ProAccessRepository {

    var restoreCalls = 0
        private set

    var syncCalls = 0
        private set

    val purchasedPackages = mutableListOf<ProPackage>()

    var calls = 0
        private set

    /** Every identify, in order, including failed attempts. */
    val identities = mutableListOf<String>()

    /** Answers identify calls in order; once empty, identify succeeds. */
    val identifyResults = ArrayDeque<EmptyResult<DataError.Remote>>()

    override suspend fun hasProAccess(): Result<Boolean, DataError.Remote> {
        calls++
        return result
    }

    override suspend fun getOfferings(): Result<List<ProPackage>, DataError.Remote> = offeringsResult

    override suspend fun purchase(proPackage: ProPackage): Result<Boolean, PurchaseError> {
        purchasedPackages += proPackage
        return purchaseResult
    }

    override suspend fun restorePurchases(): Result<Boolean, DataError.Remote> {
        restoreCalls++
        return restoreResult
    }

    override suspend fun syncPurchases(): Result<Boolean, DataError.Remote> {
        syncCalls++
        return syncResult
    }

    override suspend fun identify(appUserId: String): EmptyResult<DataError.Remote> {
        identities += appUserId
        return identifyResults.removeFirstOrNull() ?: Result.Success(Unit)
    }
}
