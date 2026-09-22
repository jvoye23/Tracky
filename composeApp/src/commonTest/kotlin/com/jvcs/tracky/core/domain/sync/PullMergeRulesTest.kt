package com.jvcs.tracky.core.domain.sync

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The pull writes the whole server tree into Room, so these rules are the only thing standing
 * between a refresh and the loss of work that has not been uploaded yet.
 */
class PullMergeRulesTest {

    @Test
    fun serverWins_whenThereIsNoLocalRow() {
        assertTrue(serverWinsOnPull(localUpdatedAtEpochMs = null, serverUpdatedAtEpochMs = 100))
    }

    @Test
    fun serverWins_whenTheLocalRowHasNeverBeenStamped() {
        assertTrue(serverWinsOnPull(localUpdatedAtEpochMs = null, serverUpdatedAtEpochMs = null))
    }

    @Test
    fun serverWins_whenItIsNewerThanLocal() {
        assertTrue(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = 200))
    }

    @Test
    fun serverWins_onATie() {
        // The server is canonical, so an equal stamp is not a reason to keep the local row.
        assertTrue(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = 100))
    }

    @Test
    fun localWins_whenItIsStrictlyNewer() {
        // The offline edit that has not been pushed yet — the case this rule exists for.
        assertFalse(serverWinsOnPull(localUpdatedAtEpochMs = 200, serverUpdatedAtEpochMs = 100))
    }

    @Test
    fun localWins_whenTheServerRowHasNoStamp() {
        assertFalse(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = null))
    }

    // ---- intervals -----------------------------------------------------------------------------
    //
    // The rule has one job: let the server close an interval this device is still showing as
    // running — that is how a timer stopped on another device stops here — without ever discarding
    // a change this device has not pushed yet.

    @Test
    fun serverWins_whenBothSidesAreClosed() {
        // A closed interval never changes again, so taking the server's copy is safe.
        assertTrue(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = 500,
                hasPendingLocalPush = false
            )
        )
    }

    @Test
    fun serverWins_whenItClosedAnIntervalThisDeviceStillHasOpen() {
        // The cross-device stop. The user pressed stop on their tablet; this closed row is the
        // only way this device will ever hear about it. The old rule refused exactly this, and the
        // timer ticked here forever.
        assertTrue(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = 800,
                hasPendingLocalPush = false
            )
        )
    }

    @Test
    fun localWins_whenAnOfflineStopHasNotDrainedYet() {
        // Closed here, still open there, and the stop is sitting in the outbox. Letting the server
        // win would reopen the row, and the queued push would then send the reopened state back —
        // the banked duration would be gone for good.
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = true
            )
        )
    }

    @Test
    fun localWins_wheneverAPushIsPending_evenIfTheServerLooksNewer() {
        // A pending operation means this device's state is newer by definition, whatever the two
        // end times happen to say.
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = 800,
                hasPendingLocalPush = true
            )
        )
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = 900,
                hasPendingLocalPush = true
            )
        )
    }

    @Test
    fun localWins_whenTheServerWouldReopenAClosedInterval() {
        // Even with nothing queued: a server copy that is still open is the server not having
        // heard the stop yet, never news. Reopening discards the duration banked into the row.
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = false
            )
        )
    }

    @Test
    fun serverWins_whenBothSidesAreStillOpen() {
        // Same interval, two views of when it started. The server arbitrated the start, and client
        // clocks are skewed, so its copy is the one to keep.
        assertTrue(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = false
            )
        )
    }
}
