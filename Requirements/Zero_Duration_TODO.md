# TODO — `totalDuration` is always 0 in Room and on the server

**Status:** diagnosed, fix not yet chosen.
**Branch investigated:** `27-Persist-server-tasks-and-intervals-on-pull`.

## Symptom

A project's total tracked time renders correctly on the project cards and on the project detail
screen, but the `totalDuration` column in Room and the `totalDuration` field on the server are `0`
(or `null`) for every project. See `Requirements/api/getProjectsResponse.json` lines 7, 24, 53, 70.

## Root cause

The value on screen and the value in the database are two different things, and only the first one
is ever computed.

**What is displayed** is `ProjectUi.totalProjectDuration`
(`composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/presentation/model/ProjectUi.kt:21-31`) —
a computed getter that folds the child tasks' `formattedDuration` strings:

```kotlin
val totalProjectDuration: String
    get() {
        val total = projectTasks?.fold(Duration.ZERO) { acc, session ->
            acc + parseDuration(session.formattedDuration)
        } ?: Duration.ZERO
        return formatDuration(total)
    }
```

It is read at `project_overview/components/ProjectCard.kt:185` and
`project_detail/ProjectDetailScreen.kt:237`. It never touches the persisted column.

**What is persisted** is `ProjectEntity.totalDuration` (`core/database/entity/ProjectEntity.kt:13`),
and nothing ever rolls a sum into it:

- `stopTask` closes the open interval and adds the elapsed span to the **task** row only —
  `features/project_tracker/data/RoomLocalProjectDataSource.kt:237` → `ProjectDao.addTaskDuration`
  (`core/database/dao/ProjectDao.kt:149`). There is no project-level equivalent anywhere in
  `ProjectDao`.
- The column therefore changes only via whole-row `@Upsert`, and the source value on those writes is
  always wrong:
  - **Creation** hardcodes `totalDurationMillis = null`
    (`project_overview/ProjectOverviewViewModel.kt:463`). `CreateProjectRequest` has no
    `totalDuration` field at all (`core/mapper/ProjectMapper.kt:143-154`), so a new project is
    `null` server-side by construction.
  - **Edit-save** round-trips through the unused `ProjectUi.totalDuration` *string*:
    `ProjectUiMapper.kt:44` formats the null/0 column into `"00:00:00:00"`, and `:61` parses that
    string straight back into `0L`. `ProjectDetailViewModel.saveProjectDetails()` (L206-221) then
    writes that `0` to Room and PUTs it to the server.
- Every mapper hop in between is a pass-through, so nothing corrects it on the way:
  `core/mapper/ProjectMapper.kt:28,47,66,161` and
  `core/data/networking/mappers/DtoMappers.kt:21,66`.

Note that `ProjectMapper.kt:66` (`ProjectWithTasksEntity.toProject()`) has the tasks in hand and
still copies the stale column — that is the most natural place for a derived value.

## Adjacent bug found while tracing (fix alongside)

`ProjectUi.toProject()` (`core/presentation/mapper/ProjectUiMapper.kt:55-71`) reconstructs the domain
object from UI strings and hardcodes `isArchived = false`, `trashedAt = null`, and omits `sortIndex`
(and `ownUpdatedAt`). Because `ProjectDetailViewModel.saveProjectDetails()` rebuilds the project
through it, **saving an edit un-archives the project, un-trashes it, and wipes its manual sort
order** — on top of zeroing the duration. Same call site, same fix.

---

## Option A — SQL roll-up on write (recommended)

Keep `totalDuration` a real persisted column and recompute it whenever a task duration changes.

**1. `core/database/dao/ProjectDao.kt`** — two new queries plus a transactional wrapper:

```kotlin
@Query("SELECT parentProjectId FROM project_records WHERE recordId = :taskId")
suspend fun getParentProjectId(taskId: String): String?

@Query("""
    UPDATE projects
    SET totalDuration = (
            SELECT COALESCE(SUM(durationMillis), 0)
            FROM project_records
            WHERE parentProjectId = :projectId
        ),
        updatedAtEpochMs = :updatedAt
    WHERE projectId = :projectId
""")
suspend fun recalculateProjectDuration(projectId: String, updatedAt: Long)

// The task total and the project roll-up must move together, or a crash between them
// leaves the parent disagreeing with its children.
@Transaction
suspend fun rollUpProjectDuration(taskId: String, updatedAt: Long) {
    getParentProjectId(taskId)?.let { recalculateProjectDuration(it, updatedAt) }
}
```

**2. `RoomLocalProjectDataSource.kt`** — call it from every path that changes a task duration:
`stopTask` (~L237), `updateTaskDuration` (L161), `deleteProjectTask` (L147), `deleteTaskInterval`
(L192). In `stopTask`, inside the existing `withContext(dbWriteDispatcher)` block:

```kotlin
projectDao.addTaskDuration(taskId, duration)
projectDao.rollUpProjectDuration(taskId, now.toEpochMilliseconds())   // ← added
```

`deleteProjectTask` must read the parent id *before* the row is gone:

```kotlin
override suspend fun deleteProjectTask(taskId: String) = withContext(dbWriteDispatcher) {
    val parentId = projectDao.getParentProjectId(taskId)
    projectDao.deleteProjectRecord(taskId)
    parentId?.let {
        projectDao.recalculateProjectDuration(it, timeProvider.nowInstant.toEpochMilliseconds())
    }
}
```

**3. `OfflineFirstProjectRepository.stopTask`** (L375-386) — push the recomputed project row through
the existing offline-first path so the server stops seeing `0` (and so it queues when offline):

```kotlin
val task = localProjectDataSource.getTaskWithIntervalsById(taskId).firstOrNull()
if (task != null) {
    upsertProjectTask(task)
    // The project row's roll-up changed too; reuse the normal upsert so it queues when offline.
    localProjectDataSource.getProjectById(task.parentProjectId)?.let { upsertProject(it) }
}
```

**4. Stop the edit path from writing `0`** — `ProjectDetailViewModel.saveProjectDetails()` should
copy onto the stored domain object instead of rebuilding it from the UI model. This also fixes the
archive/trash/sortIndex clobbering described above:

```kotlin
viewModelScope.launch {
    val stored = projectRepository.getProjectById(projectId) ?: return@launch
    projectRepository.upsertProject(
        stored.copy(
            title = newTitle,
            description = newDescription,
            colorArgb = newColor?.toArgb(),
            useLightTextColor = useLightTextColor,
        )
    )
}
```

**Cost:** one extra `PUT /api/projects/{id}` per timer stop.
**Risk:** the column stays denormalized, so any future write path that changes a task duration and
forgets the roll-up call drifts silently.

## Option B — derive in the domain model

Make the total a computed property, matching the `Timestamped.lastUpdatedAt` idiom already present in
`core/domain/model/Project.kt`:

```kotlin
data class Project(
    val projectId: String,
    val title: String,
    // … totalDurationMillis removed from the constructor …
    val projectTasks: List<ProjectTask>? = null,
    …
) : Timestamped {
    override val children: List<Timestamped> get() = projectTasks.orEmpty()

    // null projectTasks means "not loaded", not "no tasks" — same convention as children.
    val totalDurationMillis: Long?
        get() = projectTasks?.sumOf { it.durationMillis ?: 0L }
}
```

Every inbound assignment then drops out — `ProjectMapper.kt:47`, `:66`, `DtoMappers.kt:21`,
`ProjectUiMapper.kt:61`, `ProjectOverviewViewModel.kt:463`, and the three test fixtures
(`ProjectDaoPullMergeTest.kt:49`, `OfflineFirstProjectRepositoryTest.kt:57`,
`ProjectOverviewViewModelTest.kt:67`). The outbound sites (`ProjectMapper.kt:28` entity write, `:161`
`UpdateProjectRequest`, `DtoMappers.kt:66`) keep their line and now carry a real number.

**Known hole — do not take this option as written.** `upsertProject` is normally handed a project
loaded via `getProjectById`, which maps from `ProjectEntity` and therefore has `projectTasks == null`
→ the derived value is `null` → the update **writes null over a good column and PUTs null to the
server**. Needs either switching those callers to `getProjectWithTasksByProjectId` or a fallback in
the entity mapper, which a `data class` copy cannot express cleanly.

## Option C — derive + write-through

Everything in Option A, plus the domain derives from loaded tasks and falls back to the stored column
when they are not loaded:

```kotlin
data class Project(
    …
    val storedTotalDurationMillis: Long?,   // renamed constructor field, fed from the column
    val projectTasks: List<ProjectTask>? = null,
) : Timestamped {
    val totalDurationMillis: Long?
        get() = projectTasks?.sumOf { it.durationMillis ?: 0L } ?: storedTotalDurationMillis
}
```

The UI is correct the instant a task duration changes, even before the roll-up query runs, and the
column can never be nulled by a tasks-not-loaded write. Most robust; the rename ripples through all
8 mapper / ViewModel / test sites on top of everything in Option A.

## Optional scope add-on — collapse the duplicated `ProjectUi` fields

`ProjectUi` carries the duration twice (`ProjectUi.kt:13` and `:21-31`), and the getter sums
*formatted* strings, so each task loses up to 10 ms to `formatDuration`'s centisecond truncation
(`design_system/util/FormatDuration.kt`). The unused string field is also exactly what the edit-save
path parses back into `0`.

```kotlin
data class ProjectUi(
    …
    val totalDurationMillis: Long,   // was: val totalDuration: String
    …
) {
    val totalProjectDuration: String get() = formatDuration(totalDurationMillis.milliseconds)
}
```

`ProjectUiMapper.kt:44` becomes `totalDurationMillis = totalDurationMillis ?: 0L`; `:61` drops its
line. Both render sites (`ProjectCard.kt:185`, `ProjectDetailScreen.kt:237`) are untouched since they
already read `totalProjectDuration`. Churn is 12 preview fixtures across `ProjectCard.kt`,
`ProjectOverviewScreen.kt`, `ProjectArchiveScreen.kt`, `ProjectTrashScreen.kt`.

---

## Decision

- [ ] Option A — SQL roll-up on write
- [ ] Option B — derive in the domain model
- [ ] Option C — derive + write-through
- [ ] Include the `ProjectUi` cleanup add-on

**Chosen:** _(undecided)_

## Verification once implemented

- Start and stop a timer on a task, then confirm `projects.totalDuration` matches
  `SELECT SUM(durationMillis) FROM project_records WHERE parentProjectId = …`.
- Confirm the value survives a pull: `GET /api/projects` should return a non-zero `totalDuration`.
- Edit a project's title from the detail screen and confirm the duration, `isArchived`, `trashedAt`,
  and `sortIndex` are all unchanged afterwards.
- Add a JVM in-memory Room test alongside `ProjectDaoPullMergeTest` covering the roll-up after
  stop / task delete / interval delete.
