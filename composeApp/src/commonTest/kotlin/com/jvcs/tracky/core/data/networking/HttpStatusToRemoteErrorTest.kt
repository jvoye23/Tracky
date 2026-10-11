package com.jvcs.tracky.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import com.jvcs.tracky.core.domain.sync.SyncOutcome
import com.jvcs.tracky.core.domain.sync.toSyncOutcome
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.isTransient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** A 402 is the server's "not Pro yet", not an unknown failure, and it must not cost a queued write. */
class HttpStatusToRemoteErrorTest {

    @Test
    fun paymentRequiredIsItsOwnError() {
        assertThat(httpStatusToRemoteError(402)).isEqualTo(DataError.Remote.PAYMENT_REQUIRED)
    }

    @Test
    fun paymentRequiredIsTerminalPerTheBackendContract() {
        assertThat(DataError.Remote.PAYMENT_REQUIRED.isTransient()).isFalse()
    }

    @Test
    fun aQueuedWriteRefusedForPaymentIsDropped() =
        runTest {
            val result: Result<Unit, DataError.Remote> = Result.Error(DataError.Remote.PAYMENT_REQUIRED)

            assertThat(result.toSyncOutcome(onSuccess = {}, onConflict = {})).isEqualTo(SyncOutcome.DROP)
        }

    @Test
    fun anEmptyResultRefusedForPaymentIsDropped() {
        val result: EmptyResult<DataError.Remote> = Result.Error(DataError.Remote.PAYMENT_REQUIRED)

        assertThat(result.toSyncOutcome()).isEqualTo(SyncOutcome.DROP)
    }
}
