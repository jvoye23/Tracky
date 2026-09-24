package com.jvcs.tracky.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [TrackyDatabase.MIGRATION_18_19], which adds a nullable `startedByDeviceId` to
 * `task_intervals` and `sub_task_intervals` so the stranded-timer pass can tell an interval this
 * device left open from one another device is still running.
 *
 * Purely additive and deliberately backfilled with nothing. NULL has to mean "this device", since
 * that is what every pre-sync row means; stamping them with an invented id would make this
 * device's own crashed timers look foreign, and nothing would ever offer to recover them. So the
 * assertions are that the columns arrive nullable, that existing rows keep their values, and that
 * NULL is what they hold afterwards.
 *
 * The connection is opened raw rather than through Room so the v18 schema can be built verbatim
 * from the exported `18.json`, exactly as [Migration17To18Test] does.
 */
class Migration18To19Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV18()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v18 DDL for the tables this migration can reach, plus the ancestors their foreign keys
    // need, copied from composeApp/schemas/…/18.json.
    private fun createSchemaV18() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (`projectId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT, `color` INTEGER, `totalDuration` INTEGER, " +
                "`startDateTimeEpochMs` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL, " +
                "`useLightTextColor` INTEGER NOT NULL, `endDateTimeEpochMs` INTEGER, " +
                "`isArchived` INTEGER NOT NULL, `trashedAtEpochMs` INTEGER, `isPinned` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, `sortIndex` INTEGER, PRIMARY KEY(`projectId`))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_tasks` (`projectTaskId` TEXT NOT NULL, " +
                "`parentProjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, " +
                "`durationMillis` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`isTimerRunning` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER, `sortIndex` INTEGER, " +
                "PRIMARY KEY(`projectTaskId`), " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_sub_tasks` (`projectSubTaskId` TEXT NOT NULL, " +
                "`parentProjectTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT, `durationMillis` INTEGER, " +
                "`isTimerRunning` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, `sortIndex` INTEGER, PRIMARY KEY(`projectSubTaskId`), " +
                "FOREIGN KEY(`parentProjectTaskId`) REFERENCES `project_tasks`(`projectTaskId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_intervals` (`intervalId` TEXT NOT NULL, " +
                "`parentTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`startDateTimeEpochMs` INTEGER NOT NULL, `endDateTimeEpochMs` INTEGER, " +
                "`durationMillis` INTEGER NOT NULL, PRIMARY KEY(`intervalId`), " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `project_tasks`(`projectTaskId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sub_task_intervals` (`subTaskIntervalId` TEXT NOT NULL, " +
                "`parentSubTaskId` TEXT NOT NULL, `parentTaskIntervalId` TEXT NOT NULL, " +
                "`parentProjectId` TEXT NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `durationMillis` INTEGER NOT NULL, " +
                "`startedParentTimer` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`subTaskIntervalId`), " +
                "FOREIGN KEY(`parentSubTaskId`) REFERENCES `project_sub_tasks`(`projectSubTaskId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentTaskIntervalId`) REFERENCES `task_intervals`(`intervalId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }

    // ---- fixtures ------------------------------------------------------------------------------

    /** One closed task interval and one open subtask interval nested in a second, open, one. */
    private fun seedV18() {
        connection.execSQL(
            "INSERT INTO projects (projectId, title, startDateTimeEpochMs, isFinished, " +
                "useLightTextColor, isArchived, isPinned) VALUES ('p1', 'project', 0, 0, 0, 0, 0)",
        )
        connection.execSQL(
            "INSERT INTO project_tasks (projectTaskId, parentProjectId, title, durationMillis, " +
                "startDateTimeEpochMs, isFinished, isTimerRunning) " +
                "VALUES ('t1', 'p1', 'Write the report', 90000, 1000, 0, 1)",
        )
        connection.execSQL(
            "INSERT INTO project_sub_tasks (projectSubTaskId, parentProjectTaskId, parentProjectId, " +
                "title, durationMillis, isTimerRunning, startDateTimeEpochMs, isFinished) " +
                "VALUES ('s1', 't1', 'p1', 'Draft it', 5000, 1, 2000, 0)",
        )
        connection.execSQL(
            "INSERT INTO task_intervals (intervalId, parentTaskId, parentProjectId, " +
                "startDateTimeEpochMs, endDateTimeEpochMs, durationMillis) " +
                "VALUES ('i-closed', 't1', 'p1', 1000, 91000, 90000)",
        )
        connection.execSQL(
            "INSERT INTO task_intervals (intervalId, parentTaskId, parentProjectId, " +
                "startDateTimeEpochMs, endDateTimeEpochMs, durationMillis) " +
                "VALUES ('i-open', 't1', 'p1', 95000, NULL, 0)",
        )
        connection.execSQL(
            "INSERT INTO sub_task_intervals (subTaskIntervalId, parentSubTaskId, " +
                "parentTaskIntervalId, parentProjectId, startDateTimeEpochMs, endDateTimeEpochMs, " +
                "durationMillis, startedParentTimer) " +
                "VALUES ('si-open', 's1', 'i-open', 'p1', 95000, NULL, 0, 1)",
        )
    }

    // ---- query helpers -------------------------------------------------------------------------

    private fun queryLong(sql: String): Long? {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step() && !statement.isNull(0)) statement.getLong(0) else null
        } finally {
            statement.close()
        }
    }

    private fun queryText(sql: String): String? {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step() && !statement.isNull(0)) statement.getText(0) else null
        } finally {
            statement.close()
        }
    }

    private fun columnExists(table: String, column: String): Boolean =
        (queryLong("SELECT count(*) FROM pragma_table_info('$table') WHERE name = '$column'") ?: 0L) == 1L

    // ---- tests ---------------------------------------------------------------------------------

    @Test
    fun bothIntervalTablesGainTheColumn() {
        seedV18()

        TrackyDatabase.MIGRATION_18_19.migrate(connection)

        assertTrue(columnExists("task_intervals", "startedByDeviceId"))
        assertTrue(columnExists("sub_task_intervals", "startedByDeviceId"))
    }

    @Test
    fun theNewColumnsAreNullableAndStartNull() {
        seedV18()

        TrackyDatabase.MIGRATION_18_19.migrate(connection)

        // Nullable is the whole point: NULL reads as "this device", which is what a row written
        // before sync existed actually means. A NOT NULL column would need a value invented for it.
        assertEquals(
            0L,
            queryLong("SELECT \"notnull\" FROM pragma_table_info('task_intervals') WHERE name = 'startedByDeviceId'"),
        )
        assertEquals(
            0L,
            queryLong(
                "SELECT \"notnull\" FROM pragma_table_info('sub_task_intervals') " +
                    "WHERE name = 'startedByDeviceId'",
            ),
        )
        assertNull(queryText("SELECT startedByDeviceId FROM task_intervals WHERE intervalId = 'i-open'"))
        assertNull(
            queryText("SELECT startedByDeviceId FROM sub_task_intervals WHERE subTaskIntervalId = 'si-open'"),
        )
    }

    @Test
    fun existingRowsAreLeftUntouched() {
        seedV18()

        TrackyDatabase.MIGRATION_18_19.migrate(connection)

        assertEquals(90000L, queryLong("SELECT durationMillis FROM task_intervals WHERE intervalId = 'i-closed'"))
        assertEquals(91000L, queryLong("SELECT endDateTimeEpochMs FROM task_intervals WHERE intervalId = 'i-closed'"))
        assertNull(queryLong("SELECT endDateTimeEpochMs FROM task_intervals WHERE intervalId = 'i-open'"))
        // startedParentTimer is the other local-only column on this table; a rewrite would lose it.
        assertEquals(
            1L,
            queryLong("SELECT startedParentTimer FROM sub_task_intervals WHERE subTaskIntervalId = 'si-open'"),
        )
    }

    @Test
    fun aDeviceIdCanBeWrittenAfterTheMigration() {
        seedV18()

        TrackyDatabase.MIGRATION_18_19.migrate(connection)
        connection.execSQL("UPDATE task_intervals SET startedByDeviceId = 'device-a' WHERE intervalId = 'i-open'")
        connection.execSQL(
            "UPDATE sub_task_intervals SET startedByDeviceId = 'device-a' WHERE subTaskIntervalId = 'si-open'",
        )

        assertEquals("device-a", queryText("SELECT startedByDeviceId FROM task_intervals WHERE intervalId = 'i-open'"))
        assertEquals(
            "device-a",
            queryText("SELECT startedByDeviceId FROM sub_task_intervals WHERE subTaskIntervalId = 'si-open'"),
        )
    }

    @Test
    fun anOpenIntervalCanBeSelectedByOwningDevice() {
        seedV18()

        TrackyDatabase.MIGRATION_18_19.migrate(connection)
        connection.execSQL("UPDATE task_intervals SET startedByDeviceId = 'device-b' WHERE intervalId = 'i-open'")

        // The query shape the stranded-timer pass will use: own rows and legacy NULLs, never
        // another device's live timer.
        assertEquals(
            0L,
            queryLong(
                "SELECT count(*) FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
                    "AND (startedByDeviceId IS NULL OR startedByDeviceId = 'device-a')",
            ),
        )
        assertEquals(
            1L,
            queryLong(
                "SELECT count(*) FROM task_intervals WHERE endDateTimeEpochMs IS NULL " +
                    "AND (startedByDeviceId IS NULL OR startedByDeviceId = 'device-b')",
            ),
        )
    }
}
