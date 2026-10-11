package com.jvcs.tracky.core.domain.sync

import co.touchlab.kermit.Logger
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatus
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import com.jvcs.tracky.features.project.domain.models.Project
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Uploads everything a free, local-only device created into the account it has just signed in to.
 *
 * Runs while three things hold: a session exists, [LocalDataImportStore] says there is something to
 * upload, and the device is online. Until it finishes, [CloudSyncGate] keeps sync off, so the
 * upload is the only thing talking to the server about this data.
 *
 * **Why `POST /api/sync/import` and not the outbox.** None of the free tier's writes were queued,
 * and replaying them as single creates would push a project tree row by row through routes that each
 * need their parent to exist already. The import takes a whole tree in one transaction, and skips
 * ids it already has, so a retry after a dropped response is always safe.
 *
 * **What changes during the upload is not lost.** Writes keep landing in Room while the requests are
 * in flight, but none of them is queued, because sync is still off. So once the import has landed,
 * sync is switched on *first* - from then on every write pushes itself - and only then is the tree
 * read again and compared with what was uploaded. Whatever was created, changed or deleted in
 * between goes into the outbox, and the normal drain pushes it. A write that lands after the switch
 * may be queued as well as pushed; the drain's conflict handling absorbs the duplicate.
 *
 * **No cursor is stored.** The import answers with one, but if the account already had data - a free
 * user signing in to an existing account - a cursor from here would skip everything this device has
 * never pulled. The first sync after the import pulls the whole tree instead, and merges it.
 *
 * **Failures that can clear up are retried** with backoff for as long as the conditions hold. That
 * includes the `402` the server returns until RevenueCat's webhook has told it about the purchase
 * that preceded sign-up, and a `401` while the token refreshes.
 *
 * **Failures that cannot clear up are not.** The server gives the same answer every time to a
 * project it refuses (`400` for a malformed tree, `413` over the size cap, `403`, `409`). Retrying it
 * would keep sync off for the whole account forever, because sync waits for the import. So that
 * project is skipped: it stays on this device, the rest of the import goes ahead, and its title is
 * recorded in [LocalDataImportStore] so the user is told it was not uploaded.
 */
class LocalDataImporter(
    private val sessionStorage: SessionStorage,
    private val localDataImportStore: LocalDataImportStore,
    private val connectivityObserver: ConnectivityObserver,
    private val localProjectDataSource: LocalProjectDataSource,
    private val remoteSyncDataSource: RemoteSyncDataSource,
    private val pendingSyncDataSource: PendingSyncDataSource,
    private val onboardingStatusStore: OnboardingStatusStore,
    private val timeProvider: TimeProvider,
    private val applicationScope: CoroutineScope,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            combine(
                sessionStorage.observeAuthInfo(),
                localDataImportStore.observeIsPending(),
                connectivityObserver.isConnected,
            ) { authInfo, isPending, isOnline ->
                !authInfo?.accessToken.isNullOrBlank() && isPending && isOnline
            }.distinctUntilChanged()
                // collectLatest, so signing out or going offline abandons the attempt in progress.
                .collectLatest { isReady -> if (isReady) importWithRetry() }
        }
    }

    private suspend fun importWithRetry() {
        var retryDelay = INITIAL_RETRY_DELAY
        while (!importOnce()) {
            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        }
    }

    /** True once the data is on the server and sync has taken over; false to try again later. */
    private suspend fun importOnce(): Boolean {
        val uploaded =
            when (val trees = localProjectDataSource.getAllProjectTrees()) {
                is Result.Success -> {
                    trees.data
                }

                is Result.Error -> {
                    Logger.withTag(TAG).w { "could not read the local tree: ${trees.error}" }
                    return false
                }
            }

        // One project per request: each lands in its own transaction, no request comes near the
        // server's size cap, and a retry re-sends the lot, skipping whatever already landed.
        val refused = mutableListOf<Project>()
        uploaded.forEach { project ->
            val result = remoteSyncDataSource.importProjects(listOf(project))
            if (result is Result.Error) {
                if (!result.error.isRefusedForGood()) {
                    Logger.withTag(TAG).w { "import of ${project.projectId} failed, will retry: ${result.error}" }
                    return false
                }
                Logger.withTag(TAG).e { "import of ${project.projectId} refused for good: ${result.error}" }
                refused += project
            }
        }

        // Not cancellable: the switch below changes the very condition collectLatest is watching,
        // and the catch-up has to finish once the switch has happened.
        withContext(NonCancellable) {
            localDataImportStore.setPending(false)
            // The data now belongs to the account. Should the session lapse without a logout, the
            // app reopens on the welcome screen to sign in again, not as a free device whose writes
            // would never be queued.
            onboardingStatusStore.set(OnboardingStatus.NOT_STARTED)
            if (refused.isNotEmpty()) localDataImportStore.setRefusedProjects(refused.map { it.title })
            queueChangesSince(uploaded, skipped = refused.map { it.projectId }.toSet())
        }
        return true
    }

    /**
     * Whether the server has refused this project in a way it will repeat every time: the statuses
     * the import contract documents for a bad tree or an oversized one, plus a project that is not
     * this account's to create.
     *
     * Deliberately a short allowlist. Anything else - a `404` from a route that is not deployed, a
     * response that did not decode - is a fault the next attempt may not have, and skipping on it
     * would leave the project on the device for good. Retrying is safe: the import skips ids it has.
     */
    private fun DataError.Remote.isRefusedForGood(): Boolean =
        this == DataError.Remote.BAD_REQUEST ||
            this == DataError.Remote.PAYLOAD_TOO_LARGE ||
            this == DataError.Remote.FORBIDDEN ||
            this == DataError.Remote.CONFLICT

    /** A refused project is [skipped]: it never reached the server, so changes to it cannot be pushed. */
    private suspend fun queueChangesSince(uploaded: List<Project>, skipped: Set<String>) {
        val current =
            when (val trees = localProjectDataSource.getAllProjectTrees()) {
                is Result.Success -> {
                    trees.data
                }

                is Result.Error -> {
                    Logger.withTag(TAG).e { "could not re-read the tree after the import: ${trees.error}" }
                    return
                }
            }
        diffForSync(
            before = uploaded.filterNot { it.projectId in skipped }.flattenForSync(),
            after = current.filterNot { it.projectId in skipped }.flattenForSync(),
        ).forEach { change ->
            pendingSyncDataSource.enqueue(
                entityId = change.row.entityId,
                entityType = change.row.entityType,
                operationType = change.operationType,
                parentEntityId = change.row.parentEntityId,
                createdAt = timeProvider.nowInstant,
            )
        }
    }

    private companion object {
        const val TAG = "LocalDataImporter"
        val INITIAL_RETRY_DELAY: Duration = 5.seconds
        val MAX_RETRY_DELAY: Duration = 5.minutes
    }
}
