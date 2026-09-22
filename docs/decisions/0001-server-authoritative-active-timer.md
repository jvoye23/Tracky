---
status: "accepted"
date: 2026-09-22
deciders: Jörg Voye
---

# The server arbitrates which timer is running, but never how long it has run

## Context and Problem Statement

Tracky is offline-first: Room is the source of truth for every read and write, and a running timer
is simply a `task_intervals` or `sub_task_intervals` row with no end time. Once a user has more than
one device, "what is running?" stops having a local answer. The product rule is that **exactly one
timer runs per user at a time, globally** — starting one anywhere stops whatever else was running.

That rule cannot be enforced by the last-write-wins the client uses for everything else. Two devices
that are both offline each hold an open interval, and neither knows about the other's until they
reconnect. Something has to decide, and it has to decide without breaking the offline case the app
exists for.

So: who owns the running timer, and how much of it do they own?

## Decision Drivers

- Exactly one running timer per user, across all their devices — a product rule, not a nicety.
- Offline-first is not negotiable. A device with no network must still show a correct duration.
- A timer transition has to reach another device in seconds, not minutes.
- Tracked time is the user's data. No design may silently lose or double-count it.
- The client cannot resolve a conflict it cannot see: neither device knows about the other's
  interval until a third party tells it.

## Considered Options

- **Last-write-wins on interval rows**, as for every other entity.
- **A server-side live counter** — the server owns the timer and its elapsed value.
- **The server arbitrates which interval is open and when it started; elapsed stays derived on
  every device.**

## Decision Outcome

Chosen option: **the server arbitrates which interval is open and when it started, and never how
long it has run.**

A running timer remains a local row with a null end. Elapsed is computed as
`banked + (now − startedAt)` on each device independently — stored nowhere, counted nowhere. The
server answers exactly one question authoritatively: *which interval is currently open, and when
did it start?* Its rule for resolving a conflict loses no recorded work: **a later start supersedes
an earlier one and closes it at the later start time**, so the earlier session survives as a
completed interval and nobody is billed twice for the overlap.

This is what makes the feature cheap. Two devices holding the same `startedAt` agree on the number
without exchanging a single message per second, so only *transitions* travel — and those are rare.
It is also what keeps offline-first intact: once a device knows `startedAt`, it renders the correct
number forever with no network at all.

### Consequences

- Good, because a device that goes offline mid-timer keeps showing the right duration indefinitely.
  The risk becomes staleness, not drift.
- Good, because cross-device display costs no per-second traffic — only starts and stops travel.
- Good, because the "both offline, both started a timer" case has a defined, lossless resolution
  rather than an arbitrary winner.
- Good, because the client keeps one way of computing elapsed time, shared by the UI, the
  notification and the iOS Live Activity, so those surfaces cannot disagree.
- Bad, because the number now depends on two devices' clocks agreeing. `startedAt` may have come
  from the user's other phone, so local clock skew shows up directly as a wrong duration. This is
  why `ServerClock` exists, correcting from the `serverNowUtc` on every sync response.
- Bad, because a device that stops hearing from the server keeps ticking a timer that may already
  have been stopped elsewhere — correct, but not current. Mitigated by freezing a foreign timer's
  display after three missed pull cycles rather than letting it lie convincingly.
- Bad, because stopping a timer another device started must be a compare-and-swap against the
  server, so it is unavailable offline. Closing it locally would be guessing about a timer that may
  still be running.
- Neutral, because there is deliberately **no** server-side auto-close for a device that never
  comes back. The server would have to invent a duration to bank, which is the same question
  `StrandedTimerReconciler` already refuses to guess at. The timer appears to run until any device
  stops it.

### Confirmation

`scripts/verify_sync_contract.sh` exercises the arbitration rules against a deployed environment —
supersede-closes-at-the-new-start, idempotent replay, the compare-and-swap stop and both conflict
cases — and fails the build if any of them regress. On the client, `TimeManagerTest` and
`OfflineFirstRunningTimerRepositoryTest` pin that elapsed is derived, and
`OfflineFirstActiveTimerRepositoryTest` pins that the repository never banks a duration of its own.

## Pros and Cons of the Options

### Last-write-wins on interval rows

- Good, because it is the mechanism already in place for every other entity — no new concept.
- Good, because it needs no new endpoint.
- Bad, because it does not enforce the product rule at all. LWW picks a winner per *row*
  independently, so two devices that were both offline end up with two open intervals: the user is
  tracking two projects at once, both accruing the same wall-clock minutes.
- Bad, because there is no client-side rule that repairs this, since neither client knows about the
  other's interval until the server tells it.

### A server-side live counter

- Good, because "what is running and for how long" would have exactly one answer.
- Good, because clock skew between devices would stop mattering.
- Bad, because going offline would lose the clock entirely. A device with no network could not show
  a running timer, which contradicts the premise of the app.
- Bad, because it would need per-second traffic, or an interpolation scheme that reintroduces the
  same skew problem it was meant to remove.

### The server arbitrates which interval is open (chosen)

- Good, because only transitions travel, and they are rare.
- Good, because offline devices stay correct, since `startedAt` is enough to render forever.
- Bad, because it exposes clock skew, requiring `ServerClock`.
- Bad, because a stop of a foreign timer needs the network.

## More Information

Specified in `Requirements/backend-active-timer-api.md`; the design and its invariants are recorded
in `Requirements/Cross_Device_Sync_TODO.md`. Implemented across the `45-Sync-across-devices-*`
branch stack (issue 45).

Related: [0002](0002-sequence-cursor-delta-sync.md) is the transport that carries these transitions
between devices, and [0003](0003-outbox-driven-interval-merge.md) is the rule deciding when the
server's copy of an interval may overwrite the local one.

Revisit if per-second accuracy across devices ever becomes a requirement, or if the clock-skew
correction proves insufficient in the field.
