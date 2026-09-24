package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The one way a pull happens. Everything that wants fresher data asks here rather than calling
 * [DeltaSyncApplier] directly.
 *
 * **This exists for correctness, not tidiness.** Two overlapping `pullChanges()` both read the
 * cursor before either advances it, and the damage is not limited to re-applying a page. Say one
 * pull walks 10→20 and then 20→30 while a second, still holding the 10→20 page it fetched, finishes
 * and writes `setCursor(20)`: the cursor has moved **backwards**, and every row between 20 and 30 is
 * re-delivered on the next pull — or, if the store's monotonic guard rejects the write, silently is
 * not. `fullPull()` clearing the cursor while another pull is mid-flight is the same hazard with
 * sharper edges. While the only caller was a timer that fired every thirty seconds this was
 * theoretical; a socket that nudges on every transition makes it routine.
 *
 * So pulls are serialised. [request] additionally coalesces, because a burst of nudges and the
 * poll's own tick asking at the same moment should cost one round trip, not four.
 */
class SyncPullCoordinator(
    private val deltaSyncApplier: DeltaSyncApplier,
    applicationScope: CoroutineScope,
    private val coalesceWindow: Duration = COALESCE_WINDOW,
) {

    /**
     * Conflated: while a pull is in flight, any number of further requests collapse into exactly
     * one more. Dropping the extras is the point — they all mean the same thing, "there is
     * something newer", and one pull answers all of them.
     */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    private val mutex = Mutex()

    init {
        // Self-starting rather than start()-driven: the JVM target has no start() call site for
        // anything, and coalescing silently not running there would be a hard bug to see.
        applicationScope.launch {
            for (unused in requests) {
                // A nudge can land fractionally before the change it announces is visible, and a
                // burst of them should cost one pull. Both are answered by waiting a moment.
                delay(coalesceWindow)
                pullNow()
            }
        }
    }

    /** Fire-and-forget: ask for a pull soon. Safe to call as often as you like. */
    fun request() {
        requests.trySend(Unit)
    }

    /** Pull and wait for the outcome, for callers that need to know — the sync loop, and auth. */
    suspend fun pullNow(): EmptyResult<DataError> =
        mutex.withLock {
            deltaSyncApplier.pullChanges()
        }

    private companion object {
        val COALESCE_WINDOW = 200.milliseconds
    }
}
