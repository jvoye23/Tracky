package com.jvcs.tracky.core.domain.sync

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

/**
 * The pull writes the whole server tree into Room, so these rules are the only thing standing
 * between a refresh and the loss of work that has not been uploaded yet.
 */
class PullMergeRulesTest {

    @Test
    fun serverWins_whenThereIsNoLocalRow() {
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = null, serverUpdatedAtEpochMs = 100)).isTrue()
    }

    @Test
    fun serverWins_whenTheLocalRowHasNeverBeenStamped() {
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = null, serverUpdatedAtEpochMs = null)).isTrue()
    }

    @Test
    fun serverWins_whenItIsNewerThanLocal() {
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = 200)).isTrue()
    }

    @Test
    fun serverWins_onATie() {
        // The server is canonical, so an equal stamp is not a reason to keep the local row.
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = 100)).isTrue()
    }

    @Test
    fun localWins_whenItIsStrictlyNewer() {
        // The offline edit that has not been pushed yet — the case this rule exists for.
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = 200, serverUpdatedAtEpochMs = 100)).isFalse()
    }

    @Test
    fun localWins_whenTheServerRowHasNoStamp() {
        assertThat(serverWinsOnPull(localUpdatedAtEpochMs = 100, serverUpdatedAtEpochMs = null)).isFalse()
    }

    // ---- intervals -----------------------------------------------------------------------------
    //
    // The rule has one job: let the server close an interval this device is still showing as
    // running — that is how a timer stopped on another device stops here — without ever discarding
    // a change this device has not pushed yet.

    @Test
    fun serverWins_whenBothSidesAreClosed() {
        // A closed interval never changes again, so taking the server's copy is safe.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = 500,
                hasPendingLocalPush = false,
            ),
        ).isTrue()
    }

    @Test
    fun serverWins_whenItClosedAnIntervalThisDeviceStillHasOpen() {
        // The cross-device stop. The user pressed stop on their tablet; this closed row is the
        // only way this device will ever hear about it. The old rule refused exactly this, and the
        // timer ticked here forever.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = 800,
                hasPendingLocalPush = false,
            ),
        ).isTrue()
    }

    @Test
    fun localWins_whenAnOfflineStopHasNotDrainedYet() {
        // Closed here, still open there, and the stop is sitting in the outbox. Letting the server
        // win would reopen the row, and the queued push would then send the reopened state back —
        // the banked duration would be gone for good.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = true,
            ),
        ).isFalse()
    }

    @Test
    fun localWins_wheneverAPushIsPending_evenIfTheServerLooksNewer() {
        // A pending operation means this device's state is newer by definition, whatever the two
        // end times happen to say.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = 800,
                hasPendingLocalPush = true,
            ),
        ).isFalse()
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = 900,
                hasPendingLocalPush = true,
            ),
        ).isFalse()
    }

    @Test
    fun localWins_whenTheServerWouldReopenAClosedInterval() {
        // Even with nothing queued: a server copy that is still open is the server not having
        // heard the stop yet, never news. Reopening discards the duration banked into the row.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = false,
            ),
        ).isFalse()
    }

    @Test
    fun serverWins_whenBothSidesAreStillOpen() {
        // Same interval, two views of when it started. The server arbitrated the start, and client
        // clocks are skewed, so its copy is the one to keep.
        assertThat(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = null,
                hasPendingLocalPush = false,
            ),
        ).isTrue()
    }
}
