package com.jvcs.tracky.core.data.purchases

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.domain.purchases.PurchaseError
import com.jvcs.tracky.core.domain.util.DataError
import com.revenuecat.purchases.kmp.models.PurchasesError
import com.revenuecat.purchases.kmp.models.PurchasesErrorCode
import com.revenuecat.purchases.kmp.models.PurchasesException
import com.revenuecat.purchases.kmp.models.PurchasesTransactionException
import kotlin.test.Test

/** Turns RevenueCat's failures into the remote errors the rest of the app already handles. */
class PurchasesErrorMapperTest {

    private fun failure(code: PurchasesErrorCode) = PurchasesException(PurchasesError(code))

    @Test
    fun networkAndOfflineFailuresMeanNoInternet() {
        assertThat(failure(PurchasesErrorCode.NetworkError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.NO_INTERNET)
        assertThat(failure(PurchasesErrorCode.OfflineConnectionError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.NO_INTERNET)
    }

    @Test
    fun aTimedOutStoreRequestIsATimeout() {
        assertThat(failure(PurchasesErrorCode.ProductRequestTimedOut).toPurchasesDataError())
            .isEqualTo(DataError.Remote.REQUEST_TIMEOUT)
    }

    @Test
    fun aRejectedApiKeyIsUnauthorized() {
        assertThat(failure(PurchasesErrorCode.InvalidCredentialsError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.UNAUTHORIZED)
    }

    @Test
    fun backendFailuresAreServerErrors() {
        assertThat(failure(PurchasesErrorCode.UnexpectedBackendResponseError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.SERVER_ERROR)
        assertThat(failure(PurchasesErrorCode.UnknownBackendError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.SERVER_ERROR)
    }

    @Test
    fun anUnmappedCodeIsUnknown() {
        assertThat(failure(PurchasesErrorCode.StoreProblemError).toPurchasesDataError())
            .isEqualTo(DataError.Remote.UNKNOWN)
    }

    @Test
    fun aThrowableFromOutsideRevenueCatIsUnknown() {
        assertThat(IllegalStateException("not a purchases failure").toPurchasesDataError())
            .isEqualTo(DataError.Remote.UNKNOWN)
    }

    @Test
    fun aPurchaseTheUserCancelledIsCancelled() {
        val cancelled = PurchasesTransactionException(PurchasesError(PurchasesErrorCode.StoreProblemError), true)

        assertThat(cancelled.toPurchaseError()).isEqualTo(PurchaseError.Cancelled)
    }

    @Test
    fun theCancelledCodeIsCancelledEvenWithoutTheFlag() {
        assertThat(failure(PurchasesErrorCode.PurchaseCancelledError).toPurchaseError())
            .isEqualTo(PurchaseError.Cancelled)
    }

    @Test
    fun aFailedPurchaseKeepsItsRemoteError() {
        val offline = PurchasesTransactionException(PurchasesError(PurchasesErrorCode.NetworkError), false)

        assertThat(offline.toPurchaseError()).isEqualTo(PurchaseError.Remote(DataError.Remote.NO_INTERNET))
    }

    @Test
    fun aPurchaseFailureFromOutsideRevenueCatIsUnknown() {
        assertThat(IllegalStateException("not a purchases failure").toPurchaseError())
            .isEqualTo(PurchaseError.Remote(DataError.Remote.UNKNOWN))
    }
}
