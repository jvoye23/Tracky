package com.jvcs.tracky.core.domain.purchases

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Error

sealed interface PurchaseError : Error {

    /** The user closed the store sheet. Not a failure to show. */
    data object Cancelled : PurchaseError

    /** The package is no longer in the current offering, e.g. after a dashboard change. */
    data object PackageNotFound : PurchaseError

    data class Remote(val error: DataError.Remote) : PurchaseError
}
