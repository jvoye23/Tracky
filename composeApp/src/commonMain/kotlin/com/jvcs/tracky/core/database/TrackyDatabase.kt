package com.jvcs.tracky.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.jvcs.tracky.core.database.dao.PendingSyncDao
import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.PendingSyncEntity
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSubTaskEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.SubTaskIntervalEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectTaskEntity::class,
        TaskIntervalEntity::class,
        ProjectSubTaskEntity::class,
        SubTaskIntervalEntity::class,
        PendingSyncEntity::class
    ],
    version = 15,
)
@ConstructedBy(TrackyDatabaseConstructor::class)
abstract class TrackyDatabase: RoomDatabase() {
    abstract val projectDao: ProjectDao
    abstract val pendingSyncDao: PendingSyncDao

    companion object {
        const val DB_NAME = "tracky.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE projects ADD COLUMN useLightTextColor INTEGER NOT NULL DEFAULT 0")
            }
        }

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE session_intervals RENAME TO task_intervals")
                connection.execSQL("ALTER TABLE task_intervals RENAME COLUMN parentSessionId TO parentTaskId")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                // projects: rebuild with TEXT columns for startDateTimeUtc / endDateTimeUtc
                connection.execSQL(
                    """
                    CREATE TABLE projects_new (
                        projectId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT,
                        color INTEGER,
                        totalDuration INTEGER,
                        startDateTimeUtc TEXT NOT NULL,
                        isFinished INTEGER NOT NULL,
                        useLightTextColor INTEGER NOT NULL DEFAULT 0,
                        endDateTimeUtc TEXT
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO projects_new
                      (projectId, title, description, color, totalDuration,
                       startDateTimeUtc, isFinished, useLightTextColor, endDateTimeUtc)
                    SELECT projectId, title, description, color, totalDuration,
                           strftime('%Y-%m-%dT%H:%M:%fZ', startDateTimeEpochMs/1000.0, 'unixepoch'),
                           isFinished, useLightTextColor,
                           CASE WHEN endDateTimeEpochMs IS NULL THEN NULL
                                ELSE strftime('%Y-%m-%dT%H:%M:%fZ', endDateTimeEpochMs/1000.0, 'unixepoch') END
                    FROM projects
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE projects")
                connection.execSQL("ALTER TABLE projects_new RENAME TO projects")

                // project_records (ProjectTaskEntity)
                connection.execSQL(
                    """
                    CREATE TABLE project_records_new (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        parentProjectId TEXT NOT NULL,
                        description TEXT NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        startDateTimeUtc TEXT NOT NULL,
                        endDateTimeUtc TEXT,
                        isFinished INTEGER NOT NULL,
                        isTimerRunning INTEGER NOT NULL,
                        FOREIGN KEY(parentProjectId) REFERENCES projects(projectId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO project_records_new
                      (recordId, parentProjectId, description, durationMillis,
                       startDateTimeUtc, endDateTimeUtc, isFinished, isTimerRunning)
                    SELECT recordId, parentProjectId, description, durationMillis,
                           strftime('%Y-%m-%dT%H:%M:%fZ', startDateTimeEpochMs/1000.0, 'unixepoch'),
                           CASE WHEN endDateTimeEpochMs IS NULL THEN NULL
                                ELSE strftime('%Y-%m-%dT%H:%M:%fZ', endDateTimeEpochMs/1000.0, 'unixepoch') END,
                           isFinished, isTimerRunning
                    FROM project_records
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE project_records")
                connection.execSQL("ALTER TABLE project_records_new RENAME TO project_records")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_project_records_parentProjectId ON project_records(parentProjectId)"
                )

                // task_intervals
                connection.execSQL(
                    """
                    CREATE TABLE task_intervals_new (
                        intervalId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        parentTaskId TEXT NOT NULL,
                        startDateTimeUtc TEXT NOT NULL,
                        endDateTimeUtc TEXT,
                        durationMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO task_intervals_new
                      (intervalId, parentTaskId, startDateTimeUtc, endDateTimeUtc, durationMillis)
                    SELECT intervalId, parentTaskId,
                           strftime('%Y-%m-%dT%H:%M:%fZ', startDateTimeEpochMs/1000.0, 'unixepoch'),
                           CASE WHEN endDateTimeEpochMs IS NULL THEN NULL
                                ELSE strftime('%Y-%m-%dT%H:%M:%fZ', endDateTimeEpochMs/1000.0, 'unixepoch') END,
                           durationMillis
                    FROM task_intervals
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE task_intervals")
                connection.execSQL("ALTER TABLE task_intervals_new RENAME TO task_intervals")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                // task_intervals.intervalId: INTEGER AUTOINCREMENT → TEXT (UUID).
                // Existing integer ids are CAST to TEXT; only new intervals will be real UUIDs.
                connection.execSQL(
                    """
                    CREATE TABLE task_intervals_new (
                        intervalId TEXT NOT NULL PRIMARY KEY,
                        parentTaskId TEXT NOT NULL,
                        startDateTimeUtc TEXT NOT NULL,
                        endDateTimeUtc TEXT,
                        durationMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO task_intervals_new
                      (intervalId, parentTaskId, startDateTimeUtc, endDateTimeUtc, durationMillis)
                    SELECT CAST(intervalId AS TEXT), parentTaskId,
                           startDateTimeUtc, endDateTimeUtc, durationMillis
                    FROM task_intervals
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE task_intervals")
                connection.execSQL("ALTER TABLE task_intervals_new RENAME TO task_intervals")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                // All three tables: convert ISO-8601 TEXT date columns to INTEGER epoch ms,
                // rename them with the EpochMs suffix. projects also gains isArchived and
                // trashedAtEpochMs columns. ISO strings are converted via julianday so
                // millisecond precision is preserved.

                // projects
                connection.execSQL(
                    """
                    CREATE TABLE projects_new (
                        projectId TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        description TEXT,
                        color INTEGER,
                        totalDuration INTEGER,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        isFinished INTEGER NOT NULL,
                        useLightTextColor INTEGER NOT NULL DEFAULT 0,
                        endDateTimeEpochMs INTEGER,
                        isArchived INTEGER NOT NULL DEFAULT 0,
                        trashedAtEpochMs INTEGER
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO projects_new
                      (projectId, title, description, color, totalDuration,
                       startDateTimeEpochMs, isFinished, useLightTextColor, endDateTimeEpochMs,
                       isArchived, trashedAtEpochMs)
                    SELECT projectId, title, description, color, totalDuration,
                           CAST(round((julianday(startDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER),
                           isFinished, useLightTextColor,
                           CASE WHEN endDateTimeUtc IS NULL THEN NULL
                                ELSE CAST(round((julianday(endDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER) END,
                           0, NULL
                    FROM projects
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE projects")
                connection.execSQL("ALTER TABLE projects_new RENAME TO projects")

                // project_records (ProjectTaskEntity)
                connection.execSQL(
                    """
                    CREATE TABLE project_records_new (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        parentProjectId TEXT NOT NULL,
                        description TEXT NOT NULL,
                        durationMillis INTEGER NOT NULL,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        endDateTimeEpochMs INTEGER,
                        isFinished INTEGER NOT NULL,
                        isTimerRunning INTEGER NOT NULL,
                        FOREIGN KEY(parentProjectId) REFERENCES projects(projectId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO project_records_new
                      (recordId, parentProjectId, description, durationMillis,
                       startDateTimeEpochMs, endDateTimeEpochMs, isFinished, isTimerRunning)
                    SELECT recordId, parentProjectId, description, durationMillis,
                           CAST(round((julianday(startDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER),
                           CASE WHEN endDateTimeUtc IS NULL THEN NULL
                                ELSE CAST(round((julianday(endDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER) END,
                           isFinished, isTimerRunning
                    FROM project_records
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE project_records")
                connection.execSQL("ALTER TABLE project_records_new RENAME TO project_records")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_project_records_parentProjectId ON project_records(parentProjectId)"
                )

                // task_intervals
                connection.execSQL(
                    """
                    CREATE TABLE task_intervals_new (
                        intervalId TEXT NOT NULL PRIMARY KEY,
                        parentTaskId TEXT NOT NULL,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        endDateTimeEpochMs INTEGER,
                        durationMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO task_intervals_new
                      (intervalId, parentTaskId, startDateTimeEpochMs, endDateTimeEpochMs, durationMillis)
                    SELECT intervalId, parentTaskId,
                           CAST(round((julianday(startDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER),
                           CASE WHEN endDateTimeUtc IS NULL THEN NULL
                                ELSE CAST(round((julianday(endDateTimeUtc) - 2440587.5) * 86400000) AS INTEGER) END,
                           durationMillis
                    FROM task_intervals
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE task_intervals")
                connection.execSQL("ALTER TABLE task_intervals_new RENAME TO task_intervals")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE projects ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_sync_operations (
                        operationId TEXT NOT NULL PRIMARY KEY,
                        entityId TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        operationType TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(connection: SQLiteConnection) {
                // Add updatedAtEpochMs to projects and project_records for last-write-wins sync.
                connection.execSQL(
                    "ALTER TABLE projects ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0"
                )
                connection.execSQL(
                    "ALTER TABLE project_records ADD COLUMN updatedAtEpochMs INTEGER NOT NULL DEFAULT 0"
                )
                // parentEntityId lets task DELETE ops retain their parent project id.
                connection.execSQL(
                    "ALTER TABLE pending_sync_operations ADD COLUMN parentEntityId TEXT"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(connection: SQLiteConnection) {
                // updatedAtEpochMs: NOT NULL DEFAULT 0 -> nullable, treating the legacy 0
                // ("never updated") as NULL. SQLite can't drop a NOT NULL constraint in place,
                // so add a nullable temp column, convert via NULLIF, drop, and rename.
                // projects
                connection.execSQL("ALTER TABLE projects ADD COLUMN updatedAtEpochMs_tmp INTEGER")
                connection.execSQL("UPDATE projects SET updatedAtEpochMs_tmp = NULLIF(updatedAtEpochMs, 0)")
                connection.execSQL("ALTER TABLE projects DROP COLUMN updatedAtEpochMs")
                connection.execSQL("ALTER TABLE projects RENAME COLUMN updatedAtEpochMs_tmp TO updatedAtEpochMs")
                // project_records
                connection.execSQL("ALTER TABLE project_records ADD COLUMN updatedAtEpochMs_tmp INTEGER")
                connection.execSQL("UPDATE project_records SET updatedAtEpochMs_tmp = NULLIF(updatedAtEpochMs, 0)")
                connection.execSQL("ALTER TABLE project_records DROP COLUMN updatedAtEpochMs")
                connection.execSQL("ALTER TABLE project_records RENAME COLUMN updatedAtEpochMs_tmp TO updatedAtEpochMs")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(connection: SQLiteConnection) {
                // sortIndex: persisted manual order for the Custom sort filter. Nullable so existing
                // rows keep their date-based order until the user first reorders them.
                connection.execSQL("ALTER TABLE projects ADD COLUMN sortIndex INTEGER")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(connection: SQLiteConnection) {
                // task_intervals gains parentProjectId plus two cascading foreign keys, so deleting
                // a task or a project now takes its intervals with it. Before this, only
                // project_records cascaded and every interval under a deleted project was stranded.
                // SQLite can't add a foreign key in place, so the table is rebuilt.
                //
                // parentProjectId is backfilled by joining through project_records. The joins are
                // INNER on purpose: intervals whose parent task - or whose task's parent project -
                // no longer exists are exactly the orphans the old deletes left behind, and they
                // cannot satisfy the new NOT NULL foreign keys, so they are dropped here.
                connection.execSQL(
                    """
                    CREATE TABLE task_intervals_new (
                        intervalId TEXT NOT NULL PRIMARY KEY,
                        parentTaskId TEXT NOT NULL,
                        parentProjectId TEXT NOT NULL,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        endDateTimeEpochMs INTEGER,
                        durationMillis INTEGER NOT NULL,
                        FOREIGN KEY(parentTaskId) REFERENCES project_records(recordId) ON DELETE CASCADE,
                        FOREIGN KEY(parentProjectId) REFERENCES projects(projectId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    """
                    INSERT INTO task_intervals_new
                      (intervalId, parentTaskId, parentProjectId,
                       startDateTimeEpochMs, endDateTimeEpochMs, durationMillis)
                    SELECT ti.intervalId, ti.parentTaskId, pr.parentProjectId,
                           ti.startDateTimeEpochMs, ti.endDateTimeEpochMs, ti.durationMillis
                    FROM task_intervals AS ti
                    JOIN project_records AS pr ON pr.recordId = ti.parentTaskId
                    JOIN projects AS p ON p.projectId = pr.parentProjectId
                    """.trimIndent()
                )
                connection.execSQL("DROP TABLE task_intervals")
                connection.execSQL("ALTER TABLE task_intervals_new RENAME TO task_intervals")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_intervals_parentTaskId ON task_intervals(parentTaskId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_intervals_parentProjectId ON task_intervals(parentProjectId)"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(connection: SQLiteConnection) {
                // Subtasks: a fourth level under project -> task -> interval. Both tables are new,
                // so there is nothing to backfill and no table rebuild — plain CREATEs are enough.
                //
                // project_sub_tasks mirrors project_records, down to the nullable updatedAtEpochMs
                // that last-write-wins reads. It cascades from its task and, denormalised through
                // parentProjectId, straight from its project.
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_sub_tasks (
                        projectSubTaskId TEXT NOT NULL PRIMARY KEY,
                        parentProjectTaskId TEXT NOT NULL,
                        parentProjectId TEXT NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        durationMillis INTEGER,
                        isTimerRunning INTEGER NOT NULL,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        endDateTimeEpochMs INTEGER,
                        isFinished INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER,
                        FOREIGN KEY(parentProjectTaskId) REFERENCES project_records(recordId) ON DELETE CASCADE,
                        FOREIGN KEY(parentProjectId) REFERENCES projects(projectId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_project_sub_tasks_parentProjectTaskId ON project_sub_tasks(parentProjectTaskId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_project_sub_tasks_parentProjectId ON project_sub_tasks(parentProjectId)"
                )

                // sub_task_intervals additionally cascades from the task interval that encloses it:
                // timing a subtask also runs its parent task's timer, so every row here has exactly
                // one parent interval and dies with it.
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sub_task_intervals (
                        subTaskIntervalId TEXT NOT NULL PRIMARY KEY,
                        parentSubTaskId TEXT NOT NULL,
                        parentTaskIntervalId TEXT NOT NULL,
                        parentProjectId TEXT NOT NULL,
                        startDateTimeEpochMs INTEGER NOT NULL,
                        endDateTimeEpochMs INTEGER,
                        durationMillis INTEGER NOT NULL,
                        FOREIGN KEY(parentSubTaskId) REFERENCES project_sub_tasks(projectSubTaskId) ON DELETE CASCADE,
                        FOREIGN KEY(parentTaskIntervalId) REFERENCES task_intervals(intervalId) ON DELETE CASCADE,
                        FOREIGN KEY(parentProjectId) REFERENCES projects(projectId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sub_task_intervals_parentSubTaskId ON sub_task_intervals(parentSubTaskId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sub_task_intervals_parentTaskIntervalId ON sub_task_intervals(parentTaskIntervalId)"
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sub_task_intervals_parentProjectId ON sub_task_intervals(parentProjectId)"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(connection: SQLiteConnection) {
                // Records whether starting a subtask is what opened the task interval enclosing it.
                // Stopping such a subtask stops the parent task too; when the task timer was already
                // running on its own, it keeps running. Nothing else can answer that after the fact.
                //
                // Existing rows default to 0: every interval written before v15 was created by a
                // task-level start, so none of them opened their parent.
                connection.execSQL(
                    "ALTER TABLE sub_task_intervals ADD COLUMN startedParentTimer INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
