---
status: "accepted"
date: 2026-09-22
deciders: Jörg Voye
---

# The interval merge asks the outbox, not the shape of the row

## Context and Problem Statement

When a pull brings back an interval this device already has, something must decide which copy wins.
Intervals carry no timestamp of their own — they are written by a device's timer rather than edited
by hand, so there is nothing to compare and last-write-wins does not apply.

The rule in place was `serverWinsOnPullForInterval = localEnd != null && serverEnd != null` — the
server wins only between two *closed* rows. It existed to defend one case: an offline stop that has
not drained yet. The server still holds the pre-stop open copy, and taking it would reopen the
interval locally, discard the banked duration, and then push the reopened row back — losing the
measurement permanently.

But that rule also refuses the case cross-device sync exists for. A timer the user stopped on their
tablet arrives as a closed row over a locally-open one, and under this rule a pull may never close a
local interval — so it ticks on the phone forever.

Both cases look identical from the row alone: server closed, local open. What tells them apart?

## Decision Drivers

- A pull must never destroy a local change that has not reached the server.
- A pull must be able to close a timer stopped on another device — the point of the feature.
- Tracked time that is lost is unrecoverable; the user has no way to reconstruct it.
- The decision runs inside the pull transaction, five levels deep, for every interval in a page.

## Considered Options

- **Keep the closed-and-closed proxy** and accept that cross-device stops arrive only via some
  other path.
- **Give intervals their own `updatedAt`** and resolve them by last-write-wins like everything else.
- **Ask `pending_sync_operations` whether this device still owes the server a change for that exact
  row.**

## Decision Outcome

Chosen option: **ask the outbox.**

The real question was never "are both rows closed?" — it was "does this device still owe the server
a change for this interval?" The outbox answers that exactly, so the proxy is gone:

```kotlin
fun serverWinsOnPullForInterval(
    localEndDateTimeEpochMs: Long?,
    serverEndDateTimeEpochMs: Long?,
    hasPendingLocalPush: Boolean
): Boolean = when {
    hasPendingLocalPush -> false                                                 // unsent change
    localEndDateTimeEpochMs != null && serverEndDateTimeEpochMs == null -> false // never reopen
    else -> true
}
```

Rule two survives as a backstop: a closed local interval is never reopened by an open server copy,
even with an empty outbox, because a server copy that is still open is the server not having heard
the stop yet.

The same guard governs deletions. `applyDelta` is the only place a pull may delete local data, and
it skips any tombstone naming a row the outbox is carrying — a row recreated or edited offline must
outlive a tombstone the server emitted before it heard about the edit.

### Consequences

- Good, because a timer stopped on another device now closes here, which is the case the feature
  exists for.
- Good, because an undrained offline stop is still protected, and for the right reason rather than
  by a proxy that happened to correlate.
- Good, because the same question answers the tombstone case, so deletion and overwrite share one
  rule instead of two that can drift apart.
- Good, because the rule is a pure function, so it is exhaustively testable without a database.
- Bad, because correctness now depends on the outbox being accurate. A queue row dropped in error
  would let a pull overwrite unsent work — the outbox has become load-bearing for data safety, not
  just for retry.
- Neutral, because the pending ids are read **once** at the top of the transaction rather than per
  row. Five levels deep and one query per interval would have been a real cost.

### Confirmation

`ProjectDaoPullMergeTest` runs the production transaction against an in-memory database and pins
each row of the table below, including that a pulled interval this device has never seen keeps the
provenance the server sent, and that `upsertServerTree` never deletes. The rule itself is covered
directly as a pure function.

| Local | Server | Outcome |
|---|---|---|
| open, no pending op | closed | server wins — the cross-device stop |
| closed, pending stop | open | local wins until the queue drains |
| closed, acked | open (pre-stop snapshot) | local wins, rule two |
| open, same id | open, different `startedAt` | server wins; it arbitrated, and clocks are skewed |
| created offline, unknown to server | absent | untouched; only a tombstone deletes |

## Pros and Cons of the Options

### Keep the closed-and-closed proxy

- Good, because it is already written and already protects the data-loss case.
- Good, because it needs no read outside the row being merged.
- Bad, because it makes the cross-device stop impossible: a pull can never close a local interval,
  so a timer stopped on the tablet ticks on the phone until the user stops it there too.
- Bad, because it is a proxy. It correlates with the case worth defending without describing it, so
  it is right by accident and wrong wherever the correlation breaks.

### Give intervals their own `updatedAt`

- Good, because it would reuse the mechanism every other entity already uses.
- Bad, because an interval has no meaningful edit time — it is written by a timer, not edited, so
  the field would only ever record when a device happened to push.
- Bad, because intervals resolve a duplicate CREATE by retrying it as an UPDATE, not by
  last-write-wins, so the column would be dead weight on the hot path.
- Bad, because it would still not answer "is there an unsent local change?", which is the actual
  question.

### Ask the outbox (chosen)

- Good, because it answers the real question directly.
- Good, because one rule covers both overwrite and deletion.
- Bad, because it couples merge correctness to queue accuracy.

## More Information

Implemented in slice 4 of the `45-Sync-across-devices-*` stack (issue 45); the reasoning and the
outcome table are also recorded in `Requirements/Cross_Device_Sync_TODO.md`.

Related: [0002](0002-sequence-cursor-delta-sync.md) is the feed whose rows this rule merges, and
[0001](0001-server-authoritative-active-timer.md) is why a server-closed interval can now
legitimately arrive over a locally-open one at all.
