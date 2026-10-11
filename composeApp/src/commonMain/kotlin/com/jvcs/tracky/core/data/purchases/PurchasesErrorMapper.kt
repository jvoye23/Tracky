package com.jvcs.tracky.core.data.purchases

import com.jvcs.tracky.core.domain.purchases.PurchaseError
import com.jvcs.tracky.core.domain.util.DataError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException

/**
 * Maps a RevenueCat failure to a [DataError.Remote].
 *
 * Only the codes that change what the caller should do are classified; purchase-flow codes
 * (cancelled, already owned, ...) cannot come out of a customer-info read and fall through to
 * [DataError.Remote.UNKNOWN] with everything else.
 */
fun Throwable.toPurchasesDataError(): DataError.Remote {
    if (this !is PurchasesException) return DataError.Remote.UNKNOWN
    return when (code) {
        PurchasesErrorCode.NetworkError,
        PurchasesErrorCode.OfflineConnectionError,
        -> DataError.Remote.NO_INTERNET

        PurchasesErrorCode.ProductRequestTimedOut -> DataError.Remote.REQUEST_TIMEOUT

        PurchasesErrorCode.InvalidCredentialsError -> DataError.Remote.UNAUTHORIZED

        PurchasesErrorCode.UnexpectedBackendResponseError,
        PurchasesErrorCode.UnknownBackendError,
        -> DataError.Remote.SERVER_ERROR

        else -> DataError.Remote.UNKNOWN
    }
}

/**
 * Maps a failed purchase to a [PurchaseError], separating a user cancel from a real failure.
 *
 * The cancelled code is checked as well as the flag, since not every cancel path sets the flag.
 */
fun Throwable.toPurchaseError(): PurchaseError {
    val isCancelled =
        (this is PurchasesTransactionException && userCancelled) ||
            (this is PurchasesException && code == PurchasesErrorCode.PurchaseCancelledError)
    return if (isCancelled) PurchaseError.Cancelled else PurchaseError.Remote(toPurchasesDataError())
}
