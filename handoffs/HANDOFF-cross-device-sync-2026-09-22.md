# Handoff: Cross-device timer sync (issue 45)
> Generated: 2026-09-22 | Branch: `45-Sync-across-devices-21-stale-foreign-timer-guard` (stack tip) | Status: ready-for-review

## Goal

Start tracking a project on one device, stop it on another, and see the same live duration
everywhere — without giving up offline-first. Sync is the paid feature: free stays local-only, Pro
syncs. Exactly one timer runs per user, globally.

## Progress Summary

**Phases 1 and 2 are code-complete and pushed.** 22 slices in a linear stack, plus a docs branch —
23 PRs open (#120–#142). 632 jvm tests green, all three targets compiling. The deployed backend is
verified green at 48/48 contract assertions.

What remains is **not** code: the two-device end-to-end pass, which needs two real devices and
cannot be done from a session. After that, phase 3 (realtime) is the next build.

## Completed Work

- **Phase 1 — sync foundations (slices 1–9)** — device identity, `startedByDeviceId` + Room
  migration 18→19, device-scoped stranded-timer reconciliation, the outbox-driven interval merge,
  the persisted sequence cursor, the delta feed decoder, `applyDelta`, and clock-skew correction.
  Files: `core/domain/device/DeviceIdProvider.kt`, `core/domain/sync/*`,
  `core/database/dao/ProjectDao.kt`, `core/domain/util/ServerClock.kt`.
- **Contract verification against the live backend** — found and fixed one client bug and one
  backend bug. Files: `scripts/verify_sync_contract.sh`.
- **Slice 9b — `startedByDeviceId` across the wire.** Files:
  `core/data/networking/dto/ProjectDto.kt`, `core/data/networking/mappers/SyncChangesMapper.kt`,
  `core/data/networking/mappers/DtoMappers.kt`, `core/database/dao/ProjectDao.kt`.
- **Phase 2 — the active timer (slices 10–18)** — domain models and wire DTOs, the mapper, the Ktor
  data source, echo application, `OfflineFirstActiveTimerRepository` (start/stop), and routing both
  the task and subtask timer paths through it. Files: `core/domain/timer/*`, `core/data/timer/*`,
  `features/project/data/task/OfflineFirstTaskRepository.kt`,
  `features/project/data/subtask/OfflineFirstSubTaskRepository.kt`.
- **Phase 2 — presentation (slices 19–21)** — banked duration summed from closed intervals,
  `RunningTimer.isForeign`, and the stale-foreign-timer freeze. Files:
  `features/project/data/timer/OfflineFirstRunningTimerRepository.kt`,
  `core/domain/util/TimeManager.kt`, `core/domain/sync/SyncRecency.kt`.
- **Documentation** — four MADR ADRs under `docs/decisions/` with an index, the working record, and
  a paste-ready backend prompt.

## What Worked

- **Verifying against the deployed backend before writing more client code.** It cost about an hour
  and found two real bugs that would each have been invisible in unit tests.
- **Mutating a guard away and confirming a test fails.** This caught a vacuous test (see below) and
  a piece of dead code. Do this for every guard whose job is *not* to do something.
- **Reusing `upsertServerTree` for the active-timer echo** rather than writing a second merge path,
  so the refuse-to-reopen and keep-provenance rules exist in exactly one place.
- **Measuring with `count_diff.py` before committing, not after.** Three slices came in over 400 and
  were split at a real functional seam rather than an arbitrary one.

## What Failed

- **Two guard tests passed vacuously.** `FakeLocalTaskDataSource.stopTask` did not bank the
  interval's duration the way `ProjectDao.addTaskDuration` does, so the double-count the guard
  prevents could not occur in the fake either. Symptom was an assertion failing for the *wrong*
  reason: `expected:<60000> but was:<0>`. The same shape recurred for
  `FakeLocalSubTaskDataSource.stopSubTask`, which recorded nothing. **Both fakes now model the
  production behaviour** — if you add a guard, check the fake can actually show the thing it guards.
- **`onResume`'s foreign-timer check was dead code.** Written per the plan, then mutated away with
  no test failing. The collector clears `paused` the moment any timer starts, so a non-null snapshot
  already implies nothing is running. Removed rather than shipped unreachable.
- **`echo "$json"` in zsh corrupts JSON.** zsh expands backslash escapes, so a `\n` inside a JSON
  string literal becomes a real newline. Presented as
  `jq: parse error: Invalid string: control characters from U+0000 through U+001F must be escaped at line 2, column 23`
  and looked exactly like a malformed server response. Use `printf '%s'` or redirect curl to a file.
  `scripts/verify_sync_contract.sh` is `#!/bin/bash` for this reason.
- **`datetime.UTC` does not exist on this machine's Python.**
  `AttributeError: module 'datetime' has no attribute 'UTC'` — python3 is 3.9. Use
  `datetime.timezone.utc`.
- **A `runBlocking` test ending on `assertNotNull` fails the whole class.**
  `org.junit.runners.model.InvalidTestClassError: ... Method neverDeletesARowTheEchoDidNotMention() should be void`
  — reported as `initializationError`, not as a test failure. End on `assertEquals`/`assertNull`/
  `assertTrue`, or add a trailing `Unit`.
- **zsh does not word-split unquoted variables.** A `for b in $CHAIN` loop silently treated 22
  branch names as one string. Use an array: `CHAIN=(a b c)` then `"${CHAIN[@]}"`.

## Decision Log

| Decision | Options Considered | Choice | Rationale |
|---|---|---|---|
| Who owns the running timer | LWW on interval rows; server-side live counter; server arbitrates *which* interval is open | Server arbitrates which, never how long | LWW leaves two offline devices each holding an open interval; a server counter loses the clock offline. ADR 0001 |
| What orders the change feed | `updated_at_utc`; server-assigned `change_seq` | `change_seq` | Client timestamps are not monotonic, so a skewed clock skips a row permanently and silently. ADR 0002 |
| When a pulled interval wins | closed-and-closed proxy; give intervals `updatedAt`; ask the outbox | Ask `pending_sync_operations` | The proxy was right by accident — it blocked the cross-device stop this feature exists for. ADR 0003 |
| Where the echo is applied | new merge path; `applyDelta`; `upsertServerTree` | `upsertServerTree` via `applyTimerEcho` | An echo names changed rows, never removed ones, and that method promises never to delete |
| How a refusal is surfaced | `Result.Error(CONFLICT)`; sealed outcome | `ActiveTimerChange.Rejected` | A 409 carries the timer that *is* running; an error would throw that away and cost a round trip |
| Who banks a stopped duration | the active-timer repository; the caller | The caller | Only the caller knows whose timer it was; the server's task row already carries a foreign one's time |
| Where sync recency lives | field on `ProjectSyncManager`; own object | `SyncRecency` | Lets the timer read it without depending on what drives syncing, and lets a test pin it without a sync loop |
| Stale threshold | one pull cycle; three | 15 minutes (three cycles) | One failed sync must not freeze a healthy timer |

## Key Interfaces & Function Signatures

```kotlin
// core/domain/timer/ActiveTimerRepository.kt
suspend fun start(taskInterval: TaskInterval, subTaskInterval: SubTaskInterval? = null): EmptyResult<DataError>
suspend fun stop(intervalId: String, kind: ActiveTimerKind, endedAt: Instant): EmptyResult<DataError>

// core/domain/timer/RemoteActiveTimerDataSource.kt
suspend fun getActive(): Result<ActiveTimer?, DataError.Remote>              // 204 -> Success(null)
suspend fun start(request: StartActiveTimer): Result<ActiveTimerChange, DataError.Remote>
suspend fun stop(intervalId: String, endedAt: Instant): Result<ActiveTimerChange, DataError.Remote>

// core/domain/timer/ActiveTimerChange.kt  — a 409 is Success(Rejected), never Result.Error
sealed interface ActiveTimerChange {
    val active: ActiveTimer?
    val serverNow: Instant?
    data class Applied(active, touchedTaskIntervals: List<TaskInterval>,
                       touchedSubTaskIntervals: List<SubTaskInterval>, serverNow) : ActiveTimerChange
    data class Rejected(active, serverNow) : ActiveTimerChange
}

// core/domain/timer/ForeignTimer.kt  — null means "this device"
fun isForeignTimer(startedByDeviceId: String?, thisDeviceId: String): Boolean

// core/domain/sync/SyncRecency.kt
val lastSuccessfulSync: StateFlow<Instant?>   // null until the first successful pull of the process
fun markSynced(at: Instant)

// core/domain/util/TimeManager.kt
data class RunningTimerTick(val timer: RunningTimer, val elapsed: Duration, val isStale: Boolean = false)

// features/project/domain/project/LocalProjectDataSource.kt
suspend fun applyTimerEcho(taskIntervals: List<TaskInterval>, subTaskIntervals: List<SubTaskInterval>): EmptyResult<DataError.Local>

// core/database/dao/ProjectDao.kt
suspend fun getBankedTaskDuration(taskId: String): Long        // SUM of closed intervals, not the task row
suspend fun getBankedSubTaskDuration(subTaskId: String): Long

// core/data/networking/HttpClientExt.kt  — safeCall split so a caller can read a non-2xx body
suspend inline fun safeResponse(execute: () -> HttpResponse): Result<HttpResponse, DataError.Remote>
fun httpStatusToRemoteError(status: Int): DataError.Remote
```

## Files Modified

Full diff: `git diff cdb1596..45-Sync-across-devices-21-stale-foreign-timer-guard`. Highlights:

**Feature code:**
- `core/domain/timer/{ActiveTimer,ActiveTimerChange,ActiveTimerRepository,RemoteActiveTimerDataSource,ForeignTimer}.kt` — the active-timer contract
- `core/data/timer/{KtorRemoteActiveTimerDataSource,OfflineFirstActiveTimerRepository}.kt` — the remote call and the offline-first wrapper
- `core/data/networking/{HttpClientExt.kt,dto/ActiveTimerDto.kt,mappers/ActiveTimerMapper.kt}` — wire layer
- `core/domain/sync/SyncRecency.kt`, `core/domain/util/TimeManager.kt` — the stale-timer freeze
- `features/project/data/task/OfflineFirstTaskRepository.kt`,
  `features/project/data/subtask/OfflineFirstSubTaskRepository.kt` — routing, and the double-count guards
- `features/project/data/timer/OfflineFirstRunningTimerRepository.kt` — `isForeign`, banked from a SUM
- `core/database/dao/ProjectDao.kt` — `applyDelta`, `upsertServerTree`, the two banked-duration queries

**Tests:** `OfflineFirstActiveTimerRepositoryTest`, `KtorRemoteActiveTimerDataSourceTest`,
`ActiveTimerMapperTest`, `ActiveTimerDtoTest`, `RoomLocalProjectDataSourceEchoTest`,
`ProjectDaoPullMergeTest`, `TimeManagerTest`, `TimerNotificationCoordinatorTest`, `ProjectFakes.kt`.

**Config/Build:** `gradle/libs.versions.toml`, `composeApp/build.gradle.kts` — `ktor-client-mock`
added to `commonTest`.

**Docs/tooling (on `45-Sync-across-devices`, not the stack):** `docs/decisions/*`,
`scripts/verify_sync_contract.sh`, `Requirements/Cross_Device_Sync_TODO.md`,
`Requirements/backend-subtask-stop-fix.md`.

## Git State

- **Branch:** `45-Sync-across-devices-21-stale-foreign-timer-guard` (stack tip), 24 commits ahead of `main`
- **Docs branch:** `45-Sync-across-devices`, 15 commits ahead of `main` — a *parallel* branch, not the stack's base
- **Uncommitted changes:** working tree clean. Untracked and deliberately so: `.claude/`,
  `Requirements/DesignRequirements/Tracky App.zip`
- **All 23 branches pushed**, every local branch in sync with its upstream
- **Recent commits at the tip:**
  - `638496f` — feat(timer): stop a foreign timer ticking once its news goes stale
  - `5f76361` — feat(timer): tell a foreign timer apart in the UI
  - `f1df8e1` — fix(timer): bank the running timer from its closed intervals
  - `8e78086` — feat(timer): route a subtask's start and stop through the server
  - `bba8a27` — feat(timer): route a task's start and stop through the server

## Build & Test State

- **Build:** passes — `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinIosSimulatorArm64`,
  `:composeApp:compileAndroidMain` all clean
- **Tests:** 632 passing, 0 failing (`./gradlew :composeApp:jvmTest`)
- **Backend contract:** `./scripts/verify_sync_contract.sh` → 48 passed, 0 failed
- **Verification set:**
  ```
  ./gradlew :composeApp:jvmTest :composeApp:compileKotlinJvm \
            :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileAndroidMain
  ```
  The Android task is `compileAndroidMain`, **not** `compileDebugKotlinAndroid`.
- **Slice size:** `python3 .claude/pl-coding-skills/skills/stacked-pr-slicer/scripts/count_diff.py --staged --exclude 'composeApp/schemas/**'`
  — the exclude is required; Room's generated schema export is hundreds of lines per migration.

## Known Issues & Blockers

- **The two-device E2E pass has never been run.** It is the only thing between this and a usable
  feature, and it needs two real devices. Nothing blocks it.
- **Nothing renders `RunningTimerTick.isStale`.** The number stops moving, which is the correctness
  half, but no surface says why. Needs string resources and Compose work.
- **Pause is refused for a foreign timer but still offered.** `TimerNotificationCoordinator` does
  nothing, which is honest but confusing. The Android notification layout and the iOS Live Activity
  should hide the button; `onTimerNotificationPause` / `onTimerNotificationResume` keep their
  signatures and become "may do nothing".
- **`fullResyncRequired` is still unexercised.** Nothing has aged past the 90-day tombstone
  retention, so that path cannot be tested yet.
- **`POST /api/sync/import` exists but is unverified** beyond returning `{cursor, serverNowUtc}` for
  an empty body. Only needed for phase 5.
- **Room is pinned to a single connection** (comment in `CoreDataModule.kt`) because concurrent bulk
  sync writes plus timer writes corrupted the WAL pool. Relevant if you add another writer.
- **The iOS Live Activity must be verified on hardware** — the simulator does not drive
  `Text(timerInterval:)` correctly.

## Next Steps

1. **Merge the PRs.** Start with **#142** (docs) — it carries `cdb1596`, which is also the parent of
   slice 1, so merging it first collapses #120's diff to just its own five files. Then #120→#141 in
   branch-number order, retargeting each to `main` as its predecessor lands (automatic if you merge
   with "delete branch").
2. **Run the two-device E2E pass.** Start on Android and confirm the second device adopts the timer
   and shows the same duration; stop on the second and confirm the Android notification clears;
   start on both while offline, reconnect, and confirm one timer survives with the other closed at
   the winner's start; delete a project on one device and confirm it disappears on the other; kill
   the app mid-timer and confirm the stranded dialog appears **only** on the device that started it.
   Expect up to five minutes for a transition — that is `ProjectSyncManager`'s pull throttle, not a
   bug. Both devices need a login; **do not use logout to switch accounts**, it wipes the local
   database.
3. **Finish the presentation gaps** — files: `features/project/presentation/project_detail/*`,
   `androidMain/.../notification/TimerNotificationFactory.kt`, and the iOS Live Activity. Render
   `isStale` ("running on another device · last synced …") and hide Pause when
   `RunningTimer.isForeign`.
4. **Phase 3 — realtime.** Spec: `Requirements/backend-realtime-and-devices-api.md`, not
   implemented. Do this *after* the E2E pass, or a sync bug and a push bug become indistinguishable.
   Give `RealtimeTimerConnection` a `start()`/`stop()` shape from the beginning so it binds as a
   `single(createdAtStart = true)` in three lines rather than a refactor.
5. **Phase 4 — entitlement.** ADR 0004 is `proposed`; supersede rather than edit it if the reasoning
   changes once built. Must land before any release that exposes sync.
6. **Phase 5 prerequisites, doable any time and needing no backend:** the app currently requires a
   session (`ProjectOverviewViewModel.observeProjects()` gates on it) and logout wipes the local
   database. Both must change before an anonymous free tier is possible.
