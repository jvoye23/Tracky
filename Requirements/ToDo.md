# Cross-device timer sync — working record

Issue 45, branch stack `45-Sync-across-devices-*`. Written 2026-09-21 as a handoff: phase 1 is
built, phase 2 is not, and the session that did the work was running out of context.

This file is self-contained. Read it instead of re-deriving anything — several decisions below are
load-bearing and non-obvious, and getting them wrong reintroduces the exact bugs they exist to
prevent.

---

## The goal

Start tracking a project on one device, stop it on another, and see the same live duration
everywhere — without giving up offline-first. Sync is the paid feature: free stays local-only,
Pro syncs.

Decisions taken with Jörg before any code:

| Question | Decision |
|---|---|
| Concurrency | **Exactly one running timer per user, globally.** Starting anywhere stops anything else. |
| Realtime transport | WebSocket while foregrounded, silent push (FCM / APNs) in the background. |
| Billing | RevenueCat, with a webhook making the backend the authority on entitlement. |
| Free tier | **True anonymous mode** — no account, no server data. Login exists only for Pro. |
| Existing users | Free trial of Pro, then downgrade to local-only. Server data retained. |

---

## Status

**Phase 1 is complete.** Nine local branches off `45-Sync-across-devices`, each under the
400-net-line cap, **576 jvm tests green**, all three targets compiling. **Nothing is pushed**, per
the stacked-branch workflow (push once the stack is finished).

| Branch (prefix `45-Sync-across-devices`) | Commit | Lines | What |
|---|---|---|---|
| `-1-device-id` | `9c7e418` | 195 | mint-once installation UUID in DataStore |
| `-2-interval-device-column` | `0e30db2` | 359 | `startedByDeviceId` + Room migration 18→19 |
| `-3-device-scoped-reconciler` | `29dbed8` | 143 | stop parking other devices' live timers |
| `-4-merge-rule` | `ee705dc` | 277 | let a pull close a timer stopped elsewhere |
| `-5-sync-cursor-store` | `0a5f453` | 192 | persisted sequence cursor |
| `-6-delta-dto-and-datasource` | `887c24d`, `0649661` | 372 | decode `GET /api/sync/changes` |
| `-7-delta-dao` | `33b93a6` | 311 | apply a page + tombstoned deletions |
| `-8-delta-applier` | `84bf3cc` | 286 | swap the full pull for a delta |
| `-9-server-clock` | `85b2d9a` | 270 | measured clock-skew correction |

Base branch `45-Sync-across-devices` holds `cdb1596`, the four backend specs.

**Backend:** `backend-delta-sync-api.md` and `backend-active-timer-api.md` are **implemented and
deployed**. `backend-realtime-and-devices-api.md` and `backend-pro-entitlement-api.md` are not.

**Phase 2 has not been started.**

---

## The invariants

### The server arbitrates *which* interval is open, never *how long* it has run

A running timer is a `task_intervals` / `sub_task_intervals` row with `endDateTimeEpochMs IS NULL`.
Elapsed is `banked + (now − startedAt)`, derived on every device, stored nowhere and counted
nowhere.

| Fact | Authority |
|---|---|
| *Which* interval is open, and its `startedAt` | the server (Pro, when reachable) |
| What the UI reads | always local Room — never the network |
| The elapsed number | nobody; derived |

This is the whole design. Two devices holding the same `startedAt` agree on the number without
exchanging a single message per second, so only *transitions* travel and they are rare. It is also
what keeps offline-first intact: once a device knows `startedAt` it renders the correct number
forever with no network. **A server-side live counter would mean going offline loses the clock.**

The cost is clock skew, which is what `ServerClock` corrects (slice 9).

### Offline behaviour

| Situation | Behaviour |
|---|---|
| Adopting device B goes offline mid-timer | keeps ticking, and stays **correct** — it has `startedAt`. The risk is staleness, not drift: if A stops while B is offline, B keeps showing it running until reconnect. Slice 13 freezes the display past a threshold rather than lying with a live tick. |
| B tries to stop a foreign timer offline | **refused.** Stop is a compare-and-swap against the server; closing locally would be guessing about a timer that may still be running on A. |
| Owner device A goes offline mid-timer | unchanged from today: counts locally, stop banks locally and queues. B still shows it running, which is truthful — that was the last known state. B converges when A's queue drains. |
| Both offline, both start a timer | both hold a local open interval. On reconnect the server processes them in arrival order; the later start supersedes and closes the earlier **at the later start time**. One timer survives, no tracked time is lost, nothing double-counts. |
| A device offline past the tombstone retention window | `fullResyncRequired` → full pull, then re-read `/api/timer/active` **last**. |
| Owner device never comes back | the timer appears to run forever elsewhere. Any device can stop it — that is the feature — and slice 13 stops it ticking misleadingly. **No server-side auto-close, on purpose:** it would have to invent a duration to bank, the same question `StrandedTimerReconciler` refuses to guess at. |

### The interval merge asks the outbox, not the row

`serverWinsOnPullForInterval` used to be `localEnd != null && serverEnd != null` — "the server wins
only between two closed rows". That was a *proxy* for the one case worth defending: an offline stop
that has not drained, which the server's stale open copy would reopen, and which the queued push
would then send back reopened, losing the banked duration permanently.

But the proxy also refused the case this feature exists for. A timer the user stopped on their
tablet arrives as a closed row over a locally-open one, and under the old rule a pull could never
close a local interval — so it ticked here forever.

`pending_sync_operations` answers the real question exactly, so the proxy is gone:

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

| Local | Server | Outcome |
|---|---|---|
| open, no pending op | closed | **server wins — this is the cross-device stop** |
| closed, pending stop | open | local wins until the queue drains |
| closed, acked | open (pre-stop snapshot) | local wins, rule 2 |
| open, same id | open, different `startedAt` | server wins; it arbitrated, and client clocks are skewed |
| open interval A | open interval B | the delta also carries A-closed, so both land in one transaction — one Room emission, no flicker |
| created offline, unknown to server | absent | untouched; only an explicit tombstone deletes, and only with no pending op |

`ProjectDao.upsertServerTree` reads the pending ids **once** at the top rather than per row — it
runs inside the transaction and is five levels deep.

### `startedByDeviceId` null means "this device"

Nullable column on both interval tables, added in migration 18→19, deliberately not backfilled.
Every row written before multi-device sync means "this device", and that is what null reads as.
**Reading null as foreign would make this device's own crashed timers unrecoverable** — nothing
would ever offer to reclaim them.

It is what lets `StrandedTimerReconciler` tell "I crashed with a timer running" (park it, ask the
user) from "the user's other phone is tracking right now" (adopt it, show it ticking). Without the
column the second case is indistinguishable from the first, and every cold start would interrogate
the user about someone else's live timer.

The wire has no such field, so the DTO mappers take it as a **required parameter** — a push passes
the value off the row it sent, a pull passes null and the merge preserves whatever the local row
holds. Same pattern `startedParentTimer` already used. A blind write of the server echo blanks it.

### Tombstones skip anything the outbox is carrying

`applyDelta` is the first and only place a pull may delete local data. `upsertServerTree` promises
never to delete and must keep promising it, because in a full-tree pull an absent row may be one
this device created offline. A tombstone is not an absence — it is the server stating a fact.

But a row recreated or edited offline must outlive a tombstone the server emitted *before* it heard
about the edit, or the pull destroys undelivered work. Levels are deleted parents-first, so a child
tombstone arriving with its parent's is a harmless no-op after the local cascade.

### The cursor is a sequence, and it advances only after the page lands

Wire timestamps are client-supplied and persisted verbatim, so two devices with skewed clocks write
them out of order — a device could advance past a row written later but stamped earlier and miss it
permanently. Only a server-assigned `change_seq` orders the server's own writes.

Persisted in DataStore, not in memory: a cursor that reset on launch would make every cold start a
full resync, which is the cost the feed exists to avoid. Never moves backwards. Cleared on logout —
the next account has its own feed.

**Advancing before the page lands would skip the rest of that change set on every later pull,
permanently.** `DeltaSyncApplier` follows `hasMore` to the end of the feed, and refuses to loop on
a server that sets `hasMore` without moving the cursor.

### Pause stays device-local

`TimerNotificationCoordinator` holds it as `private var paused`, in memory, implemented as
stop-then-start. Leave it. A paused timer is a closed interval, so other devices correctly see "not
running" — truthful, and it costs no schema, no wire format and no backend work. Adding `pausedAt`
means a third state in every query, which this codebase explicitly rejected.

Phase 2 must make it **unavailable for a foreign timer** (pausing someone else's timer would
globally stop it — that is Stop, not Pause), and `onResume()` must no-op and clear `paused` when
the current timer is foreign, or Resume silently steals the timer using a minutes-old snapshot.

---

## Next steps

### 1. Verify phase 1 against the live backend — do this first

Every line of phase 1 was written blind against a spec. The backend has since shipped, so this is
the first chance to check the contract.

- **`parentProjectId` on every flat row**, at all four levels (tasks, task intervals, subtasks,
  subtask intervals). Reported done, but check it anyway: the client **silently drops** a flat row
  without one, because the column is `NOT NULL` locally and backs the cascade onto projects. The
  symptom is missing tasks and intervals, not an error. Note this requirement was added to
  `backend-delta-sync-api.md` in commit `0649661`, which is only on the stack branches — the copy
  on `main` and on the base branch still lacks it.
- `cursor` is monotonic and is the highest `change_seq` *included*, not the server's current max.
- Tombstones are emitted for cascaded children, not only the row the user deleted.
- `serverNowUtc` is present, so `ServerClock` gets a sample.
- `fullResyncRequired` on a cursor older than retention, and the client falls back to
  `fetchProjects()` and clears the cursor.
- Paging: `hasMore` with a cursor that advances, and rows sharing one `change_seq` never split
  across pages.

Two devices on one account end to end: start on Android, confirm the second device adopts the timer
and shows the same duration; stop on the second, confirm the Android notification clears; start on
both while offline, reconnect, confirm one timer survives and the other is closed at the winner's
start; delete a project on one device and confirm it disappears on the other; kill the app
mid-timer and confirm the stranded dialog appears **only** on the device that started it.

### 2. Phase 2 — slices 10 to 13

Branch off the tip of the phase-1 stack. Line estimates are against the 400 cap.

**S10 `-10-active-timer-remote` (~300)** — `ActiveTimer`, `ActiveTimerRepository`,
`RemoteActiveTimerDataSource`, the DTOs and `KtorRemoteActiveTimerDataSource`. The response must
carry **every interval the server touched**, not just the new one — that is what lets the losing
device converge from the response instead of waiting for the next pull.

**S11 `-11-active-timer-repository` (~360)** — `OfflineFirstActiveTimerRepository`. Start is an
optimistic local open stamped with this device plus `PUT /api/timer/active`, then apply every
returned row. Stop is the compare-and-swap `POST /api/timer/active/stop`, then apply the echo.
On transient failure **reuse the existing interval queue** rather than adding a new `entityType`,
so offline start/stop degrades to exactly today's behaviour and only takeover semantics are lost.
`OfflineFirstTaskRepository.startProjectTask` / `stopProjectTask` and the subtask equivalents route
through it when online. Tightest slice — split the task and subtask paths if it runs over.

Tests: takeover closes the previous interval from the echo; a CAS-rejected stop re-syncs instead of
closing locally; offline start falls back to the local path; and **a foreign stop never calls
`addTaskDuration`** — the sharpest double-count risk in the whole plan, because the server's task
row already carries the banked total.

**S12 `-12-foreign-timer-presentation` (~290)** — `RunningTimer.isForeign`;
`OfflineFirstRunningTimerRepository` injects `DeviceIdProvider` and **switches `bankedDuration` to
a `SUM` of closed intervals** (see below); `RunningTimerTick` carries `isForeign`;
`TimerNotificationCoordinator` refuses pause/resume for a foreign timer; `ProjectDetailViewModel`
stops branching on the denormalised `session.isTimerRunning` and reads `TimeManager.taskStates`
instead — that flag is a second source of truth which will disagree once a pull sets it from
another device.

> **`bankedDuration` is a real hole until S12 lands.** It currently reads
> `project_tasks.durationMillis`, which a device that just adopted a foreign timer may not have
> caught up on — so it shows the wrong number. Replace it with
> `SELECT COALESCE(SUM(durationMillis),0) FROM task_intervals WHERE parentTaskId = :taskId AND
> endDateTimeEpochMs IS NOT NULL` plus the subtask twin. The timer then cannot disagree with the
> interval table, it converges the instant the closing interval arrives rather than waiting for the
> task row's LWW to go the right way, and the double-count class disappears entirely
> (`addTaskDuration` is only ever called from a local stop). Cost is one indexed aggregate per
> emission; both foreign keys are already indexed.

**S13 `-13-stale-foreign-timer-guard` (~200, recommended)** — `ProjectSyncManager` exposes
`lastSuccessfulSync`; a foreign timer whose last sync is older than a threshold renders frozen as
"running on another device · last synced …" instead of ticking, and Stop is disabled offline. This
closes the "device offline for three days shows 72:00:00" hole.

### DI and start-up

All new singletons bind in `core/data/di/CoreDataModule.kt`. **Phases 1–2 need zero new lines in
`androidApp/.../app/TrackyApplication.kt` and `composeApp/src/iosMain/.../KoinHelper.kt`** — as long
as the delta pull stays folded into the existing `ProjectSyncManager` rather than becoming a fifth
always-on singleton, and `ActiveTimerRepository` stays passive (driven by user actions and the sync
manager, not its own loop).

That changes in phase 3, when the WebSocket connection becomes a `single(createdAtStart = true)`
with a `.start()`. Give `RealtimeTimerConnection` a `start()`/`stop()` shape from the beginning so
that slice is three lines rather than a refactor.

Flag to whoever owns the Swift side: `KoinHelper`'s exported `onTimerNotificationPause` /
`onTimerNotificationResume` keep their signatures but become "may do nothing" for a foreign timer,
so the Live Activity may need to hide those buttons. Out of scope for phases 1–2.

### 3. Phases 3 to 5 — specced only

| Phase | Contents | Backend |
|---|---|---|
| 3 | WebSocket (foreground) + FCM/APNs (background) + ActivityKit push for the Live Activity | `backend-realtime-and-devices-api.md`, not implemented |
| 4 | RevenueCat, entitlement gate, paywall, settings screen | `backend-pro-entitlement-api.md`, not implemented |
| 5 | Anonymous mode, optional login, bulk import on upgrade, trial + downgrade | uses `POST /api/sync/import` |

Phase 5 is larger than it looks: the app currently requires a session
(`ProjectOverviewViewModel.observeProjects()` gates on it) and logout wipes the local database.
Both must change before an anonymous tier is possible.

---

## Working rules, and traps already hit

- **Commits carry no AI attribution.** Repo rule at `.claude/pl-coding-skills/skills/git-commit/`:
  Conventional Commits, imperative, no co-author line, no tooling footer. Confirmed explicitly.
- **400 net added lines per slice, tests included.** `count_diff.py` needs
  `--exclude 'composeApp/schemas/**'` — Room's generated schema export is not in its default
  exclude list and it is several hundred lines per migration.
- **JUnit rejects a test whose `runBlocking` block ends on a value-returning assertion** such as
  `assertNotNull`. It fails as `initializationError` for the whole class, not as the test. End on
  `assertEquals` / `assertNull` / `assertTrue`, or add a trailing `Unit`. Cost three round trips.
- **DataStore refuses a second active instance over one file per process.** "It persisted" is
  tested with a second *consumer* over the same store, not a second store.
- **zsh does not word-split unquoted variables** — pass git pathspecs explicitly, not via `$VAR`.
- Verification set:
  ```
  ./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm \
            :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileAndroidMain
  ```
  The Android task is `compileAndroidMain`, **not** `compileDebugKotlinAndroid`.
- `local.properties` carries `BASE_URL`, `TEST_EMAIL`, `TEST_PASSWORD` — enough to drive the live
  backend from the Android E2E tests in `androidApp/src/androidTest/`.
- Room is pinned to a single connection (comment in `CoreDataModule.kt`) because concurrent bulk
  sync writes plus timer writes corrupted the WAL pool. Relevant if you add another writer.
- The iOS Live Activity must be verified on hardware — the simulator does not drive
  `Text(timerInterval:)` correctly.
- Line numbers cited in older notes may have drifted; phase 1 touched most of these files.

---

## Open questions to carry forward

- **Phase 1 changes behaviour visibly on upgrade:** the first pull closes every local open interval
  the server considers closed, including rows the old reconciler would have parked. Intended, and
  an improvement, but it belongs in the PR description.
- Four MADR ADRs under `docs/decisions/` were planned and never written, for the four
  hard-to-reverse choices: server-authoritative active timer, sequence-cursor delta sync,
  outbox-driven interval merge, server-enforced entitlement via RevenueCat. The repo has an
  `architecture-decision-record` skill for it. `docs/decisions/` does not exist yet.
- Nothing in the stack is pushed. Per the workflow, push once the stack is finished — arguably now,
  since phase 1 is complete and phase 2 is a separate stack.
