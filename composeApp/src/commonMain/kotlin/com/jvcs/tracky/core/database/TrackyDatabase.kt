package com.jvcs.tracky.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.entity.TaskIntervalEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectTaskEntity::class,
        TaskIntervalEntity::class
    ],
    version = 9,
)
@TypeConverters(RoomConverters::class)
@ConstructedBy(TrackyDatabaseConstructor::class)
abstract class TrackyDatabase: RoomDatabase() {
    abstract val projectDao: ProjectDao

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
                connection.execSQL("ALTER TABLE projects ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE projects ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE project_records ADD COLUMN isSynced INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE project_records ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}