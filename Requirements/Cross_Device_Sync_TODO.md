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
deployed**, and both were verified clause by clause against the live server on 2026-09-21 — 34/34
assertions pass. `backend-realtime-and-devices-api.md` and `backend-pro-entitlement-api.md` are not
implemented.

**Phase 2 is underway.** The verification found one phase-1 bug, fixed as slice `9b`; the remote
layer landed as `10`–`12`, the active-timer repository as `13`–`15`, and the task-side wiring as
`16`–`18` and the presentation as `19`–`21`. **Phase 2 is code-complete**: 632 jvm tests green,
all three targets compiling, still nothing pushed. Two things stand between this and a usable
feature — one backend bug (the subtask-stop table under "Next steps") and the two-device pass,
which is only meaningful once that is fixed.

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
| Adopting device B goes offline mid-timer | keeps ticking, and stays **correct** — it has `startedAt`. The risk is staleness, not drift: if A stops while B is offline, B keeps showing it running until reconnect. Slice 19 freezes the display past a threshold rather than lying with a live tick. |
| B tries to stop a foreign timer offline | **refused.** Stop is a compare-and-swap against the server; closing locally would be guessing about a timer that may still be running on A. |
| Owner device A goes offline mid-timer | unchanged from today: counts locally, stop banks locally and queues. B still shows it running, which is truthful — that was the last known state. B converges when A's queue drains. |
| Both offline, both start a timer | both hold a local open interval. On reconnect the server processes them in arrival order; the later start supersedes and closes the earlier **at the later start time**. One timer survives, no tracked time is lost, nothing double-counts. |
| A device offline past the tombstone retention window | `fullResyncRequired` → full pull, then re-read `/api/timer/active` **last**. |
| Owner device never comes back | the timer appears to run forever elsewhere. Any device can stop it — that is the feature — and slice 19 stops it ticking misleadingly. **No server-side auto-close, on purpose:** it would have to invent a duration to bank, the same question `StrandedTimerReconciler` refuses to guess at. |

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

### 1. Verify phase 1 against the live backend — DONE 2026-09-21

Driven against the live VPS with the `local.properties` credentials. **34/34 contract assertions
pass. The server is fully compliant with both shipped specs.** Confirmed directly:

- **`parentProjectId` is present and non-null on all 309 flat rows**, at all four levels. This was
  the one that would have failed silently.
- `cursor` is the highest `change_seq` *included*, not the server's max (`since=cursor` → empty),
  monotonic, and a `since` newer than anything on the server is echoed back rather than erroring.
- Paging is exact: a full walk at `limit=2` took 158 pages and reproduced the unpaged pull
  row-for-row — 315 rows, zero duplicates, zero omissions, cursor strictly advancing.
- Cascaded tombstones are emitted for the whole subtree (`project`, `task`, `task_interval`).
- `serverNowUtc` on every response; `since=-5` → `400`.
- All ten active-timer clauses hold: `204` when idle, `touched` carries the new interval, an
  identical replay leaves `startedAtUtc` alone, a supersede closes the previous interval **exactly
  at the new one's `startedAtUtc`** and returns both, a stop naming a non-active interval is `409`
  with the current timer in the body, restarting a closed interval is `409`, and
  `startedByDeviceId` survives a closing `PUT` that omits the field.
- `POST .../intervals` accepts and persists `startedByDeviceId`, per spec section 4.

Not exercisable yet: `fullResyncRequired`. Nothing has aged past the 90-day tombstone retention, so
`since=1` correctly returns the full set with `fullResyncRequired: false`. Re-check once the
account has a real retention gap.

The probe is committed as `scripts/verify_sync_contract.sh` — 48 assertions over both specs, run
it with no arguments. It reads `BASE_URL` / `TEST_EMAIL` / `TEST_PASSWORD` from the gitignored
`local.properties`, creates a throwaway project, exercises the timer against it and deletes it, so
the account is left as it was found. **Two assertions fail today and should**: they are the open
backend bug below, and they are how to know when it is closed.

> **zsh trap, for whoever writes the next probe:** `echo "$json"` **expands `\n` inside JSON string
> literals** and corrupts the payload — zsh's `echo` interprets backslash escapes where bash's does
> not. It looks exactly like a malformed server response. Use `printf '%s'` or redirect curl
> straight to a file.

#### Finding — `startedByDeviceId` never crosses the wire, in either direction

**This is a real bug in phase 1, and it disables the mechanism slices 1–3 exist to provide.**

The server sends `startedByDeviceId` on both interval levels, in the delta feed *and* in the nested
`GET /api/projects`. The client throws it away:

1. `TaskIntervalDto` and `SubTaskIntervalDto` (`core/data/networking/dto/ProjectDto.kt`) do not
   declare the field, and the Json is configured `ignoreUnknownKeys = true`
   (`HttpClientFactory.kt:34`), so it is **discarded silently**.
2. `SyncChangesMapper.kt:32,39` and `DtoMappers.kt:55,79` hardcode `startedByDeviceId = null`.
3. `CreateTaskIntervalRequest` / `CreateSubTaskIntervalRequest` do not send it either.

`upsertServerTree` then does `incoming.startedByDeviceId ?: local?.startedByDeviceId`. With
`incoming` always null and `local` null for a row this device has never seen, **a pulled foreign
interval lands with `startedByDeviceId = null` — which this design reads as "this device".** The
in-code comment ("A row this device has never seen gets the defaults, which is what it should
have") was written when the wire genuinely had no such field. It does now.

Consequences, both of which the column was added to prevent:

- `StrandedTimerReconciler` would park another device's **live** timer on cold start and interrogate
  the user about it — exactly the failure slice 3 describes.
- In phase 2, `RunningTimer.isForeign` would be `false` for a genuinely foreign timer, so every
  foreign-timer guard silently disengages — including the one stopping `addTaskDuration` from
  double-counting, which this document calls the sharpest such risk in the whole plan.

Latent **only** because no client has ever written a non-null value: all 285 existing interval rows
are null. It goes live the moment S11 starts sending `deviceId` to `PUT /api/timer/active`.

**Fix as slice `9b`, before S10** (`-9b-interval-device-id-wire`, ~120 lines). Declare
`startedByDeviceId: String? = null` on both interval DTOs and pass `dto.startedByDeviceId` at the
four call sites that currently pass null. Keep the mapper *parameter* — `KtorRemoteIntervalDataSource`
(`:29,:37`) and `KtorRemoteSubTaskIntervalDataSource` (`:67`) legitimately override it with the
local row's value on a push echo, and `startedParentTimer` still has no wire counterpart. The
`?:` fallback in `upsertServerTree` then becomes correct on its own. The **push** half — sending
the id on the plain interval routes — belongs in S11, which already injects `DeviceIdProvider`.

#### Finding — a replayed stop returns `touched: []`

Not a bug; the server is right. But S11 must not read an empty echo as failure. Measured: the first
`POST /api/timer/active/stop` returns the closed interval in `touched` with its duration; an
identical replay returns `200` with `active: null` and **`touched: []`**, and the stored duration
does not move. A client that treats "no rows echoed" as "the stop did not take" would leave the
local row open forever on any retry.

#### Still outstanding — the two-device pass

Not automatable from here; needs two real devices on one account. Start on Android, confirm the
second device adopts the timer and shows the same duration; stop on the second, confirm the Android
notification clears; start on both while offline, reconnect, confirm one timer survives and the
other is closed at the winner's start; delete a project on one device and confirm it disappears on
the other; kill the app mid-timer and confirm the stranded dialog appears **only** on the device
that started it. **Do this after slice 9b** — before it, the stranded-dialog check is guaranteed to
fail.

### 2. Phase 2 — the remote layer is DONE; the repository is next

The remote half landed on 2026-09-21. It did **not** fit in one slice: the original S10 estimate of
~300 was written before the house KDoc style was accounted for, and the four files plus tests came
to 497 lines of production code alone. It is three slices instead, all green and all under the cap:

| Branch (prefix `45-Sync-across-devices`) | Commit | Lines | What |
|---|---|---|---|
| `-9b-interval-device-id-wire` | `455f442` | 121 | carry `startedByDeviceId` across the wire |
| `-10-active-timer-model-and-dto` | `c09cf65` | 331 | `ActiveTimer`, `ActiveTimerChange`, the DTOs |
| `-11-active-timer-mapper` | `1d835af` | 278 | payloads → interval rows, split by level |
| `-12-active-timer-datasource` | `862a14f` | 382 | `KtorRemoteActiveTimerDataSource`, `safeResponse` |

**599 jvm tests green**, all three targets compiling. Still nothing pushed.

Worth knowing before the next slice:

- `ActiveTimerChange` is **sealed**: `Applied` carries the touched rows already split into task and
  subtask intervals, `Rejected` carries the timer that is actually running. A `409` never surfaces
  as a `Result.Error`, so the repository can converge on the server's answer without a second call.
- `safeCall` was split. `safeResponse` keeps the transport-failure handling and returns whatever
  the server sent; `httpStatusToRemoteError` holds the status mapping. Existing callers unchanged.
- `ktor-client-mock` is now a `commonTest` dependency, so the next slices can script status codes.
- Slice numbering has shifted twice now. Do not trust a number in an older note; trust the table.

### The active-timer repository — mostly DONE

`OfflineFirstActiveTimerRepository` landed 2026-09-21, as three slices rather than one. The ~360
estimate was again written before the KDoc and fixtures this repo actually carries: the first cut
measured 619.

| Branch (prefix `45-Sync-across-devices`) | Commit | Lines | What |
|---|---|---|---|
| `-13-timer-echo-application` | `0350229` | 231 | `LocalProjectDataSource.applyTimerEcho` |
| `-14-active-timer-start` | `1607c66` | 400 | task start, queue fallback, refusal handling |
| `-15-active-timer-stop` | `7856af1` | 105 | the compare-and-swap stop |

**614 jvm tests green**, all three targets compiling, nothing pushed.

Decisions worth knowing:

- **The echo goes through `upsertServerTree`, not `applyDelta`.** An echo names rows the server
  changed, never rows it removed, and `upsertServerTree` is the one that promises never to delete.
  Reusing the pull merge means the refuse-to-reopen and keep-provenance rules exist once.
- **A refusal triggers `DeltaSyncApplier.pullChanges()`**, not a local close. The 409 body names
  what is running but not when the interval this device asked about ended, and inventing that is
  the guess `StrandedTimerReconciler` refuses. That is why the repository depends on the applier.
- **The repository never banks a duration** — pinned by a test. Banking stays with the caller,
  which is the only place that knows whose timer it was.
- **A queued stop carries its `ActiveTimerKind`**, so a subtask interval is queued as
  `ENTITY_SUBTASK_INTERVAL`. Queuing it as a task interval would drain to the wrong endpoint.

**Still to do, in order:**

**`-16-active-timer-subtask-start`** — `c6df214`, 85 lines. Done.

**`-17-task-timer-routing`** — `bba8a27`, 226 lines. Done. The task paths now go through the
server, and the double-count guard is written and mutation-checked.

**`-18-subtask-timer-routing`** — `8e78086`, 173 lines. Done. Both timer paths now go through the
server, and both double-count guards are mutation-checked.

#### Backend bug found while wiring it — needs fixing before the two-device pass

Verified against the deployed backend on 2026-09-21, by probing all four subtask cases:

| Case | Server behaviour | Correct? |
|---|---|---|
| `sub_task` start naming a task interval the server does not have | creates it, same id, same instant, both rows in `touched` | yes |
| `sub_task` start naming one that is already open | leaves it open, only the subtask row in `touched` | yes |
| stop a subtask that *opened* its parent interval | closes both at the same instant | yes |
| stop a subtask while the task timer was started **independently** | **closes both** | **no** |

The last row is the bug. The client tracks which timer opened which —
`SubTaskInterval.startedParentTimer` — so that a user who starts a task timer, drills into a
subtask and then stops the subtask keeps the task running. The server stops it, so the next pull
closes it on their screen and banks a duration they did not ask to end.

**The client cannot work around this.** `startedParentTimer` has no wire counterpart and never
will: which timer opened which is a purely local fact, which is why the merge rules preserve it.
The server needs its own record of it. Written up in `backend-active-timer-api.md` section 3.

Until it is fixed, the flow "start task → start subtask → stop subtask" ends both timers.

### The presentation slices — DONE

| Branch (prefix `45-Sync-across-devices`) | Commit | Lines | What |
|---|---|---|---|
| `-19-banked-duration-sum` | `f1df8e1` | 71 | bank from closed intervals, not the task row |
| `-20-foreign-timer-presentation` | `5f76361` | 119 | `RunningTimer.isForeign`, Pause refused |
| `-21-stale-foreign-timer-guard` | `638496f` | 166 | a foreign timer freezes once its news goes stale |

**632 jvm tests green**, all three targets compiling. **Phase 2 is code-complete.**

Two things the plan got wrong, worth knowing:

- **`onResume` needs no foreign check.** The plan said it must no-op and clear `paused`. It does
  not: the collector clears `paused` the moment any timer starts running, so a non-null snapshot
  already means nothing is running anywhere this device has heard about. A guard was written,
  survived being mutated away, and was removed rather than left as unreachable code.
- **`ProjectDetailViewModel` needed no change.** `updateUiWithTimerValues` already overwrites
  `isTimerRunning` from `TimeManager.taskStates` for every task and subtask, so it was never
  reading the denormalised flag the plan wanted it to stop reading.

Still open at the presentation layer, and deliberately not done here:

- **Nothing renders `RunningTimerTick.isStale` yet.** The number stops moving, which is the
  correctness half, but no surface says *why*. "running on another device · last synced …" is
  string resources and Compose in `ProjectDetailScreen` and the notification layouts.
- **Pause is refused for a foreign timer but still offered.** `TimerNotificationCoordinator`
  does nothing, which is honest but confusing. The Android notification layout and the iOS Live
  Activity should hide the button; `onTimerNotificationPause` / `onTimerNotificationResume` keep
  their signatures and become "may do nothing".

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
- **A fake that is missing production behaviour makes a test pass vacuously.** `FakeLocalTaskDataSource.stopTask`
  did not bank the interval's duration onto the task the way `ProjectDao.addTaskDuration` does, so
  the first version of the double-count guard's test could not have failed. Found by mutating
  `isForeignTimer` to return `false` and watching nothing break. **Mutate the rule and confirm the
  test catches it** before believing a guard is covered.
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
