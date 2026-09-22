---
status: "accepted"
date: 2026-09-22
deciders: Jörg Voye
---

# A server-assigned sequence cursor orders the change feed, not a timestamp

## Context and Problem Statement

Until multi-device sync, the only way the server's state reached a device was `GET /api/projects`,
which returns the entire tree and is throttled to once every five minutes. That has two
consequences which block the feature outright.

**Deletions never propagate.** The client's tree-merge deliberately never deletes a local row the
server's response omits, because an omitted row is ambiguous — it may have been deleted on another
device, or created on *this* device seconds ago and not pushed yet. Deleting on that signal would
destroy offline work, so the client keeps everything, and a project deleted on a phone lives on the
tablet forever.

**A five-minute full-tree pull cannot carry a running timer.** A transition has to reach another
device in seconds, and re-downloading every project to learn that one interval closed is not
something that can be done at that frequency.

So the client needs an incremental feed. What orders it?

## Decision Drivers

- A client must never skip a change. Missing one is silent and permanent.
- Deletions have to be stated explicitly; absence is not a statement.
- The feed must be cheap enough to poll far more often than every five minutes.
- Clocks on the user's devices are not synchronised and cannot be made so.
- The existing contract — client-supplied timestamps, persisted verbatim — is correct and should
  not be broken to make sync easier.

## Considered Options

- **`updated_at_utc` as the cursor**, reusing the timestamps already on every row.
- **A server-assigned, per-user monotonic `change_seq`**, with explicit tombstones for deletions.
- **Keep the full-tree pull and poll it more often.**

## Decision Outcome

Chosen option: **a server-assigned `change_seq` on all five syncable tables, exposed through
`GET /api/sync/changes?since=<cursor>`, with tombstones for deletions.**

The cursor is the highest `change_seq` actually *included* in a response — not the server's current
maximum — so a client that stores it and passes it back can never skip a row. Deletions arrive as
explicit tombstones, including for rows removed by a cascade, because a client holding a task but
not its project would otherwise be left with orphans.

Two client-side rules are load-bearing and easy to get wrong. The cursor advances **only after the
page has landed in Room**: advancing first and failing halfway would skip the rest of that change
set on every subsequent pull, permanently. And the cursor is **persisted**, not held in memory — one
that reset on launch would make every cold start a full resync, which is the cost the feed exists to
avoid.

### Consequences

- Good, because deletions finally propagate, which is what makes a project deleted on one device
  disappear on another.
- Good, because the feed is cheap on a quiet account, so it can be polled far more often than the
  full tree could.
- Good, because ordering no longer depends on any client's clock.
- Good, because the existing timestamp contract is untouched — the client still owns the wall-clock
  meaning of its records.
- Bad, because `change_seq` must be bumped on *every* write path, including the bulk sort endpoints
  that update rows outside the normal single-row update. A reorder that forgets is invisible to
  every other device.
- Bad, because tombstones need a retention window (90 days) and a purge job, and a device offline
  past it must be told to fall back to a full pull via `fullResyncRequired`.
- Neutral, because the client stores one cursor per account and never a sequence per row —
  `change_seq` is a server-side ordering device and deliberately appears in no payload.

### Confirmation

`scripts/verify_sync_contract.sh` walks the entire feed at `limit=2` and diffs the result against
the unpaged pull, asserting no duplicates and no losses, that the cursor strictly advances, and that
a `since` equal to the cursor returns no rows. `DeltaSyncApplierTest` pins that the cursor advances
only after a page is applied and that the applier refuses to loop on a server that sets `hasMore`
without moving the cursor.

## Pros and Cons of the Options

### `updated_at_utc` as the cursor

- Good, because the column already exists on every row, so no schema change and no new concept.
- Good, because it is human-readable when debugging.
- Bad, because those columns are client-supplied ISO-8601 strings persisted verbatim — the
  documented contract, and the right one, since the client owns what the timestamp means.
- Bad, because they are therefore not server-assigned and not monotonic. Two devices with skewed
  clocks routinely write values out of order, so a device can advance its cursor past a row that was
  written later but stamped earlier and **miss it permanently**. The failure is silent.

### A server-assigned `change_seq` (chosen)

- Good, because only the server assigns it, so ordering within one user is total and reliable.
- Good, because gaps are harmless, so a single shared sequence across all tables and users is fine.
- Bad, because every write path must remember to bump it.
- Bad, because it adds a column and an index to five tables.

### Keep the full-tree pull and poll it more often

- Good, because no backend work at all.
- Bad, because it still cannot express a deletion, which is half the problem.
- Bad, because the payload is the user's entire history; polling it at timer frequency is
  indefensible on bandwidth and battery.

## More Information

Specified in `Requirements/backend-delta-sync-api.md`. Implemented in slices 5–8 of the
`45-Sync-across-devices-*` stack (issue 45).

Related: [0001](0001-server-authoritative-active-timer.md) is what produces the transitions this
feed carries, and [0003](0003-outbox-driven-interval-merge.md) decides what a pulled row may
overwrite once it arrives.

One requirement is worth restating because it fails silently: **every flat row below project level
must carry `parentProjectId`**. The client stores it as a `NOT NULL` column backing a cascading
foreign key, so a row without one cannot be written — and the client drops such a row rather than
failing the whole delta. Omitting the field loses data with no error anywhere.
