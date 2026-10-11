# Handoff: Cross-device sync — propagation fixed, presentation closed (issue 45)

> Generated: 2026-09-22 | Branch: `45-sync-5-hide-pause-foreign` (stack tip) | Status: ready-for-review
> Supersedes `HANDOFF-cross-device-sync-2026-09-22.md`

## Goal

Unchanged: start tracking on one device, stop on another, same live duration everywhere, without
giving up offline-first. This session fixed why that did not actually happen on real hardware.

## What changed since the last handoff

Phases 1–2 were merged (#120–#142, all on `main`). The two-device E2E pass was then run for the
first time and **failed**: device B only picked up device A's timer after a manual pull-to-refresh
on ProjectOverviewScreen, and an open ProjectDetailScreen never updated at all.

Two independent root causes, both found by reading and then reproduced in tests.

### RC1 — nothing ever re-triggered a pull

`ProjectSyncManager` was edge-triggered:

```kotlin
combine(online, foreground) { o, f -> o && f }.distinctUntilChanged().filter { it }
```

`distinctUntilChanged()` over a `Boolean` emits only on the false→true transition, so a device that
stayed online and foregrounded pulled **once, at launch, and never again**. `PULL_INTERVAL = 5.minutes`
read like a throttle but was a ceiling on an event that never re-fired — nothing in the chain was a
clock. The previous handoff's "expect up to five minutes for a transition, that is the pull throttle,
not a bug" was wrong: the wait was unbounded.

Corroborating: `pullChanges()` had only two production call sites, and the second
(`OfflineFirstActiveTimerRepository:142`, on a 409) fires on the device that *lost* a CAS, never on a
passive device B. Every background path — `SyncWorker`, iOS `BGAppRefresh` — is push-only.

### RC2 — the detail screen could not repaint even after a successful pull

`ProjectDetailViewModel.state` streamed only `observeProjectById` — the *project row*. The whole task
tree was a one-shot read, refreshed only by `onReturnedToScreen()`. The timer overlay did get through
(it rides `TimeManager`, a Room flow), which is why start/pause looked nearly right, but a **stop**
elsewhere banks a new duration into the task row, and the overlay then fell back to the stale
snapshot. Fixing RC1 alone would not have fixed the reported symptom.

## Completed Work — six slices, all local, none pushed

| Commit | Slice |
|---|---|
| `915008b` | `fix(sync): pull on a clock, not on an edge` — `flatMapLatest` into a 30s ticker; `lastPull` deleted |
| `5e37ed5` | `fix(sync): count a refresh as hearing from the server` — `markSynced` moved into `DeltaSyncApplier` |
| `487fc63` | `feat(sync): stream the project task tree from Room` — the `Flow` sibling DAO query |
| `5822612` | `fix(detail): stream the task tree instead of snapshotting it` — plus `reorderInFlight` |
| `5a3fda0` | `feat(timer): say when a timer belongs to another device` — `isForeign`/`isStale` to the screen |
| `0db77ed` | `feat(timer): hide Pause when another device owns the timer` — Android + iOS |

Three further defects found and fixed in passing:

- `lastPull = now` sat outside the success check, so one failed pull burned the whole window.
- Pull-to-refresh never called `markSynced` and never advanced the cursor, so a foreign timer adopted
  by refresh froze as stale 15 minutes later however recently the user pulled — and if the launch
  pull failed, `lastSuccessfulSync` stayed `null` and the staleness guard switched itself off.
- Two dead `ProjectDetailState` fields (`formattedTimerString`, `timerDuration`), read nowhere.

## What Worked

- **Reproducing both root causes as failing tests before fixing anything.** The JVM actuals of
  `ConnectivityObserver` and `AppLifecycleObserver` are both `flowOf(true)` — always online, always
  foregrounded — which is exactly the device-B case, so `ProjectSyncManagerTest` reproduces RC1 with
  no fakes at all. It reported "the feed was asked 1 time(s)" after 90 virtual seconds.
- **Mutating every guard away and re-running.** This caught two vacuous tests (below) and confirmed
  the other six guards. Do it for every guard whose job is *not* to do something.
- **Reusing the existing query for the reactive tree.** `observeProjectWithTaskTreeById` is the same
  SQL, the same relation entity and the same `toProject()` mapper as the suspend version — 31 lines.

## What Failed

- **Two new tests passed vacuously.** `FakeDetailProjectRepository` holds a `MutableStateFlow`, which
  **conflates equal values**, so `emit()` of a project equal to the one already held never emitted
  and the collector never ran. Both the reorder-guard and overlay-clobber tests passed with the guard
  deleted. Fixed by making each emission a distinct value (`.copy(description = "touched")`). Same
  shape as last session's fake-doesn't-model-production trap — check the fake can actually *show* the
  thing being guarded.
- **The plan said to hide the task card's Pause button. That was wrong and was not done.** The card's
  glyph is `Icons.Default.Pause`, but its contentDescription is `stop_timer` and it calls
  `stopProjectTask`, which routes a foreign timer through `stopForeignTimer`. **Stopping another
  device's timer is the feature.** Hiding that button would have deleted the main affordance. The
  genuine Pause is only on the notification and the Live Activity, and that is what slice 5 hides.
- **`gradle` reports `UP-TO-DATE` and silently skips a mutation.** A mutation check that prints no
  failure may mean the guard is untested *or* that nothing re-ran. Always use `--rerun-tasks`.
- **`timeout` does not exist on macOS.** `command not found: timeout`.

## Decision Log

| Decision | Options Considered | Choice | Rationale |
|---|---|---|---|
| Foreground pull cadence | 5s; two-tier 10s/60s; 30s; keep 5min | **30s** | The realtime socket becomes the fast path; this is the correctness floor. Two-tier has a bootstrap hole — B cannot know A started a timer until it pulls. One constant to change. |
| Where `markSynced` lives | the sync loop; each caller; the applier | **`DeltaSyncApplier`** | One pull, one stamp, wherever the pull came from. In the loop it silently meant "the loop heard from the server". |
| Pull-to-refresh transport | full tree; delta | **delta** | Fixes the cursor and recency together; the applier already falls back to the full tree when the feed is unusable. |
| Detail tree | keep one-shot + more triggers; reactive | **reactive, overlay re-applied after each adoption** | Removes the stale-snapshot class of bug rather than adding another trigger someone must remember to fire. |
| Where `isForeign` reaches the UI | VM collects `tick` separately; widen `TimerState` | **widen `TimerState`** | `taskStates` is what every screen reads; a second source would let the two disagree about one timer. |
| iOS: attributes or ContentState | attributes; `ContentState` | **`ContentState`** | Foreignness can change while one timer runs; an attributes change must end the activity and request a new one. |

## Key Interfaces & Function Signatures

```kotlin
// core/domain/sync/ProjectSyncManager.kt
private val FOREGROUND_PULL_INTERVAL = 30.seconds   // the one knob; raise once realtime lands

// core/domain/sync/DeltaSyncApplier.kt  — stamps SyncRecency on success, for every caller
suspend fun pullChanges(): EmptyResult<DataError>

// core/database/dao/ProjectDao.kt
fun observeProjectWithTaskTreeById(projectId: String): Flow<ProjectWithTaskTreeEntity?>

// core/domain/util/TimeManager.kt  — both flags now reach the UI
data class TimerState(isRunning, totalDuration, formattedTime, isForeign, isStale)

// core/domain/notification/TimerNotificationSession.kt  — feeds both platforms
val isForeign: Boolean = false
```

Swift: `TrackyTimerAttributes.ContentState.isForeign: Bool` → `TrackyTimerLiveActivity.swift:105`
hides `ToggleGlyph`, covering the Lock Screen and the expanded Island together.

## Build & Test State

- **648 jvm tests passing, 0 failing** (was 632; +16). iOS: 119 passing.
- All targets compile: `compileKotlinJvm`, `compileKotlinIosSimulatorArm64`, `compileAndroidMain`
  (the Android task is `compileAndroidMain`, **not** `compileDebugKotlinAndroid`).
- `./scripts/verify_sync_contract.sh` → **48 passed, 0 failed**, unchanged.
- Verification set:
  ```
  ./gradlew :composeApp:jvmTest :composeApp:iosSimulatorArm64Test :composeApp:compileKotlinJvm \
            :composeApp:compileKotlinIosSimulatorArm64 :composeApp:compileAndroidMain --rerun-tasks
  ```

## Git State

- **Branch:** `45-sync-5-hide-pause-foreign`, 6 commits ahead of `main`, **all local, nothing pushed**
- Working tree clean. Untracked and deliberately so: `.claude/`,
  `Requirements/DesignRequirements/Tracky App.zip`, `handoffs/`
- Each slice is its own branch: `45-sync-1-repeating-foreground-pull`,
  `-2-refresh-advances-cursor`, `-3-reactive-task-tree`, `-3b-detail-screen-reactive`,
  `-4-foreign-timer-on-screen`, `-5-hide-pause-foreign`
- Every slice measured under 400 net added lines (largest: 242).

## Known Issues & Blockers

- **The two-device E2E pass has not been re-run since the fix.** This is the only thing between here
  and a working feature, and it needs two real devices. Nothing blocks it. The acceptance test is in
  the plan file; the two that matter most are (1) B adopts A's timer within ~30s with no gesture, on
  both the overview and an already-open detail screen, and (2) B repaints while sitting on
  ProjectDetailScreen the whole time.
- **The stale caption carries no timestamp.** It reads "not confirmed recently" rather than
  "last synced 14:32". Plumbing the instant needs `SyncRecency` reaching presentation; deliberately
  deferred.
- **`RemoteActiveTimerDataSource.getActive()` still has zero production call sites**, though
  `Cross_Device_Sync_TODO.md:92` specs a re-read of `/api/timer/active` after a full pull. Only
  matters on the `fullResyncRequired` path, which remains unexercised.
- **`fullResyncRequired` is still unexercised** — nothing has aged past the 90-day tombstone window.
- **Room is pinned to a single connection** (`CoreDataModule.kt`) because concurrent bulk sync writes
  plus timer writes corrupted the WAL pool. **Slice 1 increases pull frequency 10×** — if WAL trouble
  reappears, that constraint is the first place to look.
- **The iOS Live Activity must be verified on hardware.** The simulator does not drive
  `Text(timerInterval:)` correctly, and slice 5's hidden toggle has not been seen on a device.

## Next Steps

1. **Re-run the two-device E2E pass.** Both devices need a login; **do not use logout to switch
   accounts**, it wipes the local database. Expect a transition within ~30s, not five minutes.
2. **Push the stack and open the PRs** once the E2E pass is green — one PR per slice branch, in
   order, retargeting each to `main` as its predecessor lands.
3. **Phase 3 — realtime.** The backend for `Requirements/backend-realtime-and-devices-api.md` **is
   already deployed**, so this is client-only: `POST /api/devices` registration, then
   `RealtimeTimerConnection` with a `start()`/`stop()` shape so it binds as
   `single(createdAtStart = true)` in three lines. On any `invalidate`/`timer` envelope call
   `deltaSyncApplier.pullChanges()` — never treat the inline payload as the only carrier of a fact.
   Then `FOREGROUND_PULL_INTERVAL` can go back up.
4. **Phase 4 — entitlement.** ADR 0004 is still `proposed`; supersede rather than edit it if the
   reasoning changes. Must land before any release that exposes sync.
5. **Phase 5 prerequisites**, doable any time and needing no backend: the app requires a session
   (`ProjectOverviewViewModel.observeProjects()` gates on it) and logout wipes the local database.
   Both must change before an anonymous free tier is possible.
