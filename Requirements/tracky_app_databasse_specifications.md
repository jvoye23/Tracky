# Tracky Database Specification

This document captures the Tracky Android/KMP Room database **as-is**, for use as input when updating the `tracky-api` Spring Boot backend. It is a faithful 1:1 description of the schema, DAO behavior, type converters, migrations, and the entity ↔ domain mapping. No backend recommendations or schema additions have been made.

Source branch: `Udated-ProjectCard`. All file paths below are relative to the repository root (`/Users/jvoye/AndroidStudioProjects/Tracky`).

---

## 1. Overview

- **Database class:** `TrackyDatabase` (abstract, extends `RoomDatabase`)
- **Source file:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/TrackyDatabase.kt`
- **Database file name:** `tracky.db` (constant `TrackyDatabase.DB_NAME`)
- **Schema version:** `3`
- **Multiplatform constructor:** `TrackyDatabaseConstructor` (declared via `@ConstructedBy`)
- **Type converters class:** `RoomConverters` (declared via `@TypeConverters`)

**Registered entities** (in declaration order):

1. `ProjectEntity`
2. `ProjectSessionEntity`
3. `SessionIntervalEntity`

**Exposed DAOs:**

- `projectDao: ProjectDao`

```kotlin
@Database(
    entities = [
        ProjectEntity::class,
        ProjectSessionEntity::class,
        SessionIntervalEntity::class
    ],
    version = 3,
)
@TypeConverters(RoomConverters::class)
@ConstructedBy(TrackyDatabaseConstructor::class)
abstract class TrackyDatabase: RoomDatabase() {
    abstract val projectDao: ProjectDao
    // ...
}
```

---

## 2. Entities

All timestamps are stored as **epoch milliseconds** (`Long`). All boolean columns are stored as Room's standard `INTEGER` (0/1). All string columns are `TEXT`.

### 2.1 `ProjectEntity` → table `projects`

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/entity/ProjectEntity.kt`
- **Foreign keys:** none
- **Indices:** none
- **Embedded objects:** none

```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey
    val projectId: String,
    val title: String,
    val description: String?,
    val color: Int?,
    val totalDuration: Long?,
    val startDateTimeEpochMs: Long,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeEpochMs: Long?,
)
```

| Column                  | Kotlin type | Nullable | Default | PK | Notes                                         |
|-------------------------|-------------|----------|---------|----|-----------------------------------------------|
| `projectId`             | `String`    | No       | —       | ✅ | Primary key (no auto-generate; client-supplied)|
| `title`                 | `String`    | No       | —       |    |                                               |
| `description`           | `String?`   | Yes      | —       |    |                                               |
| `color`                 | `Int?`      | Yes      | —       |    | ARGB int (see domain field `colorArgb`)       |
| `totalDuration`         | `Long?`     | Yes      | —       |    | Total duration in milliseconds                |
| `startDateTimeEpochMs`  | `Long`      | No       | —       |    | Epoch milliseconds                            |
| `isFinished`            | `Boolean`   | No       | —       |    | Stored as INTEGER (0/1)                       |
| `useLightTextColor`     | `Boolean`   | No       | `false` |    | Stored as INTEGER (0/1); added in v2          |
| `endDateTimeEpochMs`    | `Long?`     | Yes      | —       |    | Epoch milliseconds                            |

---

### 2.2 `ProjectSessionEntity` → table `project_records`

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/entity/ProjectSessionEntity.kt`
- **Foreign keys:** `parentProjectId` references `projects.projectId`, `onDelete = CASCADE`
- **Indices:** `Index(value = ["parentProjectId"])`
- **Embedded objects:** none

```kotlin
@Entity(
    tableName = "project_records",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["projectId"],
            childColumns = ["parentProjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["parentProjectId"])]
)
data class ProjectSessionEntity(
    @PrimaryKey
    val recordId: String,
    val parentProjectId: String,
    val description: String,
    val durationMillis: Long,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val isFinished: Boolean,
    val isTimerRunning: Boolean
)
```

| Column                  | Kotlin type | Nullable | Default | PK | Notes                                                       |
|-------------------------|-------------|----------|---------|----|-------------------------------------------------------------|
| `recordId`              | `String`    | No       | —       | ✅ | Primary key (no auto-generate; client-supplied)             |
| `parentProjectId`       | `String`    | No       | —       |    | FK → `projects.projectId`, `ON DELETE CASCADE`; indexed     |
| `description`           | `String`    | No       | —       |    | Maps to domain field `title`                                |
| `durationMillis`        | `Long`      | No       | —       |    |                                                             |
| `startDateTimeEpochMs`  | `Long`      | No       | —       |    |                                                             |
| `endDateTimeEpochMs`    | `Long?`     | Yes      | —       |    |                                                             |
| `isFinished`            | `Boolean`   | No       | —       |    | Stored as INTEGER (0/1)                                     |
| `isTimerRunning`        | `Boolean`   | No       | —       |    | Stored as INTEGER (0/1)                                     |

---

### 2.3 `SessionIntervalEntity` → table `session_intervals`

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/entity/SessionIntervalEntity.kt`
- **Foreign keys:** none declared via `@ForeignKey` annotation. The relationship to `project_records` is expressed only through the `@Relation` in `SessionWithIntervals` and the DAO queries that filter on `parentSessionId`.
- **Indices:** none
- **Embedded objects:** none
- **Added in:** schema version 3 (see `MIGRATION_2_3`)

```kotlin
@Entity(tableName = "session_intervals")
data class SessionIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val intervalId: Long = 0,
    val parentSessionId: String,
    val startDateTimeEpochMs: Long,
    val endDateTimeEpochMs: Long?,
    val durationMillis: Long
)
```

| Column                  | Kotlin type | Nullable | Default | PK | Notes                                                                  |
|-------------------------|-------------|----------|---------|----|------------------------------------------------------------------------|
| `intervalId`            | `Long`      | No       | `0`     | ✅ | Auto-generated (`AUTOINCREMENT`); pass `0` to insert                   |
| `parentSessionId`       | `String`    | No       | —       |    | Logically references `project_records.recordId` (no `@ForeignKey`)     |
| `startDateTimeEpochMs`  | `Long`      | No       | —       |    |                                                                        |
| `endDateTimeEpochMs`    | `Long?`     | Yes      | —       |    | `NULL` while the interval is still open (active timer)                 |
| `durationMillis`        | `Long`      | No       | —       |    |                                                                        |

---

## 3. Relations

### 3.1 `ProjectWithSessions` (one-to-many: project → its sessions)

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/relation/ProjectWithSessions.kt`
- **Parent column:** `projects.projectId`
- **Child column:** `project_records.parentProjectId`

```kotlin
data class ProjectWithSessions(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        parentColumn = "projectId",
        entityColumn = "parentProjectId"
    )
    val projectSessions: List<ProjectSessionEntity>
)
```

### 3.2 `SessionWithIntervals` (one-to-many: session → its intervals)

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/relation/SessionWithIntervals.kt`
- **Parent column:** `project_records.recordId`
- **Child column:** `session_intervals.parentSessionId`

```kotlin
data class SessionWithIntervals(
    @Embedded val session: ProjectSessionEntity,
    @Relation(
        parentColumn = "recordId",
        entityColumn = "parentSessionId"
    )
    val intervals: List<SessionIntervalEntity>
)
```

---

## 4. DAOs

### 4.1 `ProjectDao`

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/dao/ProjectDao.kt`

```kotlin
@Dao
interface ProjectDao { /* ... */ }
```

#### Project (`projects` table)

| Method | Annotation(s) | Signature | SQL | Returns |
|---|---|---|---|---|
| `upsertProjects` | `@Upsert` | `suspend fun upsertProjects(products: List<ProjectEntity>)` | (generated upsert) | `Unit` |
| `upsertProject` | `@Upsert` | `suspend fun upsertProject(project: ProjectEntity)` | (generated upsert) | `Unit` |
| `getProjects` | `@Query` | `fun getProjects(): Flow<List<ProjectEntity>>` | `SELECT * FROM projects ORDER BY projectId ASC` | `Flow<List<ProjectEntity>>` |
| `getProjectById` | `@Query` | `suspend fun getProjectById(id: String): ProjectEntity?` | `SELECT * FROM projects WHERE projectId = :id` | `ProjectEntity?` |
| `deleteProject` | `@Query` | `suspend fun deleteProject(projectId: String)` | `DELETE FROM projects WHERE projectId = :projectId` | `Unit` |
| `deleteAllProjects` | `@Query` | `suspend fun deleteAllProjects()` | `DELETE FROM projects` | `Unit` |

#### Project + sessions (transactional reads)

| Method | Annotation(s) | Signature | SQL | Returns |
|---|---|---|---|---|
| `getProjectsWithSessions` | `@Transaction` `@Query` | `fun getProjectsWithSessions(): Flow<List<ProjectWithSessions>>` | `SELECT * FROM projects` | `Flow<List<ProjectWithSessions>>` |
| `getProjectWithSessionsById` | `@Transaction` `@Query` | `suspend fun getProjectWithSessionsById(projectId: String): ProjectWithSessions?` | `SELECT * FROM projects WHERE projectId = :projectId` | `ProjectWithSessions?` |

#### Project records / sessions (`project_records` table)

| Method | Annotation(s) | Signature | SQL | Returns |
|---|---|---|---|---|
| `upsertProjectRecord` | `@Upsert` | `suspend fun upsertProjectRecord(record: ProjectSessionEntity)` | (generated upsert) | `Unit` |
| `deleteProjectRecord` | `@Query` | `suspend fun deleteProjectRecord(recordId: String)` | `DELETE FROM project_records WHERE recordId = :recordId` | `Unit` |
| `updateSessionDuration` | `@Query` | `suspend fun updateSessionDuration(sessionId: String, newDurationMillis: Long)` | `UPDATE project_records SET durationMillis = :newDurationMillis WHERE recordId = :sessionId` | `Unit` |
| `updateSessionTimerStatus` | `@Query` | `suspend fun updateSessionTimerStatus(sessionId: String, isRunning: Boolean)` | `UPDATE project_records SET isTimerRunning = :isRunning WHERE recordId = :sessionId` | `Unit` |
| `addSessionDuration` | `@Query` | `suspend fun addSessionDuration(sessionId: String, additionalDuration: Long)` | `UPDATE project_records SET durationMillis = durationMillis + :additionalDuration WHERE recordId = :sessionId` | `Unit` |
| `updateSessionTitle` | `@Query` | `suspend fun updateSessionTitle(sessionId: String, title: String)` | `UPDATE project_records SET description = :title WHERE recordId = :sessionId` | `Unit` |

> Note: `updateSessionTitle` writes the domain-level "title" into the `project_records.description` column (entity column is named `description`, domain field is named `title`).

#### Session + intervals (transactional reads)

| Method | Annotation(s) | Signature | SQL | Returns |
|---|---|---|---|---|
| `getSessionWithIntervalsById` | `@Transaction` `@Query` | `fun getSessionWithIntervalsById(sessionId: String): Flow<SessionWithIntervals?>` | `SELECT * FROM project_records WHERE recordId = :sessionId` | `Flow<SessionWithIntervals?>` |

#### Session intervals (`session_intervals` table)

| Method | Annotation(s) | Signature | SQL | Returns |
|---|---|---|---|---|
| `upsertSessionInterval` | `@Upsert` | `suspend fun upsertSessionInterval(interval: SessionIntervalEntity)` | (generated upsert) | `Unit` |
| `deleteIntervalsBySessionId` | `@Query` | `suspend fun deleteIntervalsBySessionId(sessionId: String)` | `DELETE FROM session_intervals WHERE parentSessionId = :sessionId` | `Unit` |
| `getOpenIntervalBySessionId` | `@Query` | `suspend fun getOpenIntervalBySessionId(sessionId: String): SessionIntervalEntity?` | `SELECT * FROM session_intervals WHERE parentSessionId = :sessionId AND endDateTimeEpochMs IS NULL LIMIT 1` | `SessionIntervalEntity?` |

---

## 5. Type Converters (`RoomConverters`)

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/database/RoomConverters.kt`
- **Registered via:** `@TypeConverters(RoomConverters::class)` on `TrackyDatabase`.

**JSON configuration** used internally:

```kotlin
private val jsonHandler = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
```

**Converter functions:**

| Direction | Function | Behavior |
|---|---|---|
| `List<ProjectSession>?` → `String?` | `fromProjectRecordList(projectSessions: List<ProjectSession>?): String?` | Returns `null` for a `null` input; otherwise serializes the list to a JSON string via `kotlinx.serialization`. |
| `String?` → `List<ProjectSession>?` | `fromJsonStringToProjectRecordList(jsonString: String?): List<ProjectSession>?` | Returns `null` for a `null` input; otherwise tries to decode the JSON string back to `List<ProjectSession>`. On any exception, prints the stack trace and returns `null`. |

**Note on usage:** No column on any current `@Entity` has type `List<ProjectSession>` / `String` that would route through these converters. They are registered on the database but currently unused in entity definitions.

---

## 6. Migrations

Both migrations are defined as `companion object` properties on `TrackyDatabase`. SQL strings are quoted verbatim from `TrackyDatabase.kt`.

### 6.1 `MIGRATION_1_2` — adds `useLightTextColor` to `projects`

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE projects ADD COLUMN useLightTextColor INTEGER NOT NULL DEFAULT 0")
    }
}
```

SQL executed:

```sql
ALTER TABLE projects ADD COLUMN useLightTextColor INTEGER NOT NULL DEFAULT 0
```

### 6.2 `MIGRATION_2_3` — creates `session_intervals`

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS session_intervals (
                intervalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                parentSessionId TEXT NOT NULL,
                startDateTimeEpochMs INTEGER NOT NULL,
                endDateTimeEpochMs INTEGER,
                durationMillis INTEGER NOT NULL
            )
        """.trimIndent())
    }
}
```

SQL executed:

```sql
CREATE TABLE IF NOT EXISTS session_intervals (
    intervalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    parentSessionId TEXT NOT NULL,
    startDateTimeEpochMs INTEGER NOT NULL,
    endDateTimeEpochMs INTEGER,
    durationMillis INTEGER NOT NULL
)
```

---

## 7. Domain Models & Entity ↔ Domain Mapping

The presentation/domain layer uses a separate set of model classes that talk in `kotlin.time.Instant` rather than epoch milliseconds. These are the shapes the API will most likely need to mirror at the DTO level.

### 7.1 Domain models

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/domain/model/Project.kt`

```kotlin
data class Project @OptIn(ExperimentalTime::class) constructor(
    val projectId: String,
    val title: String,
    val description: String?,
    val colorArgb: Int?,
    val totalDurationMillis: Long?,
    val startDateTimeUtc: Instant,
    val isFinished: Boolean,
    val useLightTextColor: Boolean = false,
    val endDateTimeUtc: Instant?,
    val projectSessions: List<ProjectSession>? = null
)

data class ProjectSession @OptIn(ExperimentalTime::class) constructor(
    val projectSessionId: String,
    val title: String,
    val durationMillis: Long?,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val isFinished: Boolean,
    val parentProjectId: String,
    val isTimerRunning: Boolean,
    val intervals: List<SessionInterval> = emptyList()
)

data class SessionInterval(
    val intervalId: Long?,
    val parentSessionId: String,
    val startDateTimeUtc: Instant,
    val endDateTimeUtc: Instant?,
    val durationMillis: Long
)
```

### 7.2 Mapper functions

- **Source:** `composeApp/src/commonMain/kotlin/com/jvcs/tracky/core/mapper/ProjectMapper.kt`
- All `Long` ↔ `Instant` conversions go through `Instant.fromEpochMilliseconds(...)` / `Instant.toEpochMilliseconds()`.

#### `ProjectEntity` ↔ `Project`

| Entity column           | Domain field           | Conversion                                                                                         |
|-------------------------|------------------------|----------------------------------------------------------------------------------------------------|
| `projectId`             | `projectId`            | identity                                                                                           |
| `title`                 | `title`                | identity                                                                                           |
| `description`           | `description`          | identity                                                                                           |
| `color`                 | `colorArgb`            | identity (renamed)                                                                                 |
| `totalDuration`         | `totalDurationMillis`  | identity (renamed)                                                                                 |
| `startDateTimeEpochMs`  | `startDateTimeUtc`     | `Long` ↔ `Instant` via `fromEpochMilliseconds` / `toEpochMilliseconds`                             |
| `isFinished`            | `isFinished`           | identity                                                                                           |
| `useLightTextColor`     | `useLightTextColor`    | identity                                                                                           |
| `endDateTimeEpochMs`    | `endDateTimeUtc`       | nullable `Long` ↔ nullable `Instant` (null preserved)                                              |
| —                       | `projectSessions`      | Not populated by `ProjectEntity.toProject()`; populated only by `ProjectWithSessions.toProject()`  |

When mapping back via `Project.toProjectEntity()`, the `projectSessions` field is **not** written to the entity (it's stored separately in `project_records`).

#### `ProjectSessionEntity` ↔ `ProjectSession`

| Entity column           | Domain field           | Conversion                                                                                          |
|-------------------------|------------------------|-----------------------------------------------------------------------------------------------------|
| `recordId`              | `projectSessionId`     | identity (renamed)                                                                                  |
| `parentProjectId`       | `parentProjectId`      | identity                                                                                            |
| `description`           | `title`                | identity (renamed — entity column is `description`, domain field is `title`)                        |
| `durationMillis`        | `durationMillis`       | `Long` ↔ `Long?`. Mapping back uses `durationMillis ?: 0L` when domain value is null.               |
| `startDateTimeEpochMs`  | `startDateTimeUtc`     | `Long` ↔ `Instant`                                                                                  |
| `endDateTimeEpochMs`    | `endDateTimeUtc`       | nullable `Long` ↔ nullable `Instant`                                                                |
| `isFinished`            | `isFinished`           | identity                                                                                            |
| `isTimerRunning`        | `isTimerRunning`       | identity                                                                                            |
| —                       | `intervals`            | Not populated by `ProjectSessionEntity.toProjectSession()`; populated only by `SessionWithIntervals.toProjectSession()` (defaults to `emptyList()`) |

#### `SessionIntervalEntity` ↔ `SessionInterval`

| Entity column           | Domain field           | Conversion                                                                                          |
|-------------------------|------------------------|-----------------------------------------------------------------------------------------------------|
| `intervalId`            | `intervalId`           | `Long` ↔ `Long?`. Mapping back uses `intervalId ?: 0` (a `0` value triggers Room auto-generation).  |
| `parentSessionId`       | `parentSessionId`      | identity                                                                                            |
| `startDateTimeEpochMs`  | `startDateTimeUtc`     | `Long` ↔ `Instant`                                                                                  |
| `endDateTimeEpochMs`    | `endDateTimeUtc`       | nullable `Long` ↔ nullable `Instant`                                                                |
| `durationMillis`        | `durationMillis`       | identity                                                                                            |

#### Aggregate mappers

- `ProjectWithSessions.toProject()` — produces a `Project` whose `projectSessions` is filled by mapping each `ProjectSessionEntity` via `toProjectSession()`. Note: those nested sessions do **not** carry intervals (the relation does not load them).
- `SessionWithIntervals.toProjectSession()` — produces a `ProjectSession` whose `intervals` is filled by mapping each `SessionIntervalEntity` via `toSessionInterval()`.

---

## 8. Schema Reference (compact)

Quick lookup of every persisted column. Types are described abstractly (no vendor SQL).

### `projects`

| Column                  | Type     | Nullable | Default | Key                     |
|-------------------------|----------|----------|---------|-------------------------|
| `projectId`             | TEXT     | No       | —       | PRIMARY KEY             |
| `title`                 | TEXT     | No       | —       |                         |
| `description`           | TEXT     | Yes      | —       |                         |
| `color`                 | INTEGER  | Yes      | —       |                         |
| `totalDuration`         | INTEGER  | Yes      | —       |                         |
| `startDateTimeEpochMs`  | INTEGER  | No       | —       |                         |
| `isFinished`            | INTEGER  | No       | —       | (boolean)               |
| `useLightTextColor`     | INTEGER  | No       | `0`     | (boolean; v2)           |
| `endDateTimeEpochMs`    | INTEGER  | Yes      | —       |                         |

### `project_records`

| Column                  | Type     | Nullable | Default | Key                                                                  |
|-------------------------|----------|----------|---------|----------------------------------------------------------------------|
| `recordId`              | TEXT     | No       | —       | PRIMARY KEY                                                          |
| `parentProjectId`       | TEXT     | No       | —       | FK → `projects.projectId`, ON DELETE CASCADE; INDEX                  |
| `description`           | TEXT     | No       | —       |                                                                      |
| `durationMillis`        | INTEGER  | No       | —       |                                                                      |
| `startDateTimeEpochMs`  | INTEGER  | No       | —       |                                                                      |
| `endDateTimeEpochMs`    | INTEGER  | Yes      | —       |                                                                      |
| `isFinished`            | INTEGER  | No       | —       | (boolean)                                                            |
| `isTimerRunning`        | INTEGER  | No       | —       | (boolean)                                                            |

### `session_intervals`

| Column                  | Type     | Nullable | Default | Key                                                                  |
|-------------------------|----------|----------|---------|----------------------------------------------------------------------|
| `intervalId`            | INTEGER  | No       | —       | PRIMARY KEY AUTOINCREMENT                                            |
| `parentSessionId`       | TEXT     | No       | —       | (logical reference to `project_records.recordId`; no `@ForeignKey`)  |
| `startDateTimeEpochMs`  | INTEGER  | No       | —       |                                                                      |
| `endDateTimeEpochMs`    | INTEGER  | Yes      | —       | NULL while interval is open                                          |
| `durationMillis`        | INTEGER  | No       | —       |                                                                      |
