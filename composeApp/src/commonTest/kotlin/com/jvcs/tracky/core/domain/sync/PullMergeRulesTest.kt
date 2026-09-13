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

    @Test
    fun serverWins_whenBothSidesAreClosed() {
        // A closed interval never changes again, so taking the server's copy is safe.
        assertTrue(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = 500
            )
        )
    }

    @Test
    fun localWins_forAnOpenLocalInterval() {
        // No end time means the timer is running on this device; a pull must not close it.
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = 800
            )
        )
    }

    @Test
    fun localWins_whenTheServerStillHasTheIntervalOpen() {
        // Every offline stop looks like this until the queue drains: closed here, still open there.
        // Letting the server win would reopen the row and discard the duration banked into it.
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = 500,
                serverEndDateTimeEpochMs = null
            )
        )
    }

    @Test
    fun localWins_whenNeitherSideIsClosed() {
        assertFalse(
            serverWinsOnPullForInterval(
                localEndDateTimeEpochMs = null,
                serverEndDateTimeEpochMs = null
            )
        )
    }
}
