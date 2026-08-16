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
 * Exercises [TrackyDatabase.MIGRATION_12_13] against a real SQLite connection.
 *
 * This is the first migration in the project that deletes user data: `task_intervals` gains two
 * NOT NULL cascading foreign keys, and the interval rows that earlier single-project and
 * single-task deletes stranded cannot satisfy them. Dropping exactly those rows — and nothing
 * else — is the behaviour worth pinning down.
 *
 * The connection is opened raw rather than through Room so the v12 schema can be built verbatim
 * from the exported `12.json`. Foreign keys are left off, which is the environment Room actually
 * runs migrations in (the generated `onOpen` turns them on only after the migration transaction
 * has committed).
 */
class Migration12To13Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV12()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v12 DDL, copied from composeApp/schemas/…/12.json so the fixture is provably the shape
    // real installs are migrating from.
    private fun createSchemaV12() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (`projectId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT, `color` INTEGER, `totalDuration` INTEGER, " +
                "`startDateTimeEpochMs` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL, " +
                "`useLightTextColor` INTEGER NOT NULL, `endDateTimeEpochMs` INTEGER, " +
                "`isArchived` INTEGER NOT NULL, `trashedAtEpochMs` INTEGER, `isPinned` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, `sortIndex` INTEGER, PRIMARY KEY(`projectId`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_records` (`recordId` TEXT NOT NULL, " +
                "`parentProjectId` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                "`durationMillis` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`isTimerRunning` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER, PRIMARY KEY(`recordId`), " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `task_intervals` (`intervalId` TEXT NOT NULL, " +
                "`parentTaskId` TEXT NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `durationMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`intervalId`))"
        )
    }

    private fun insertProject(id: String) {
        connection.execSQL(
            "INSERT INTO projects (projectId, title, startDateTimeEpochMs, isFinished, " +
                "useLightTextColor, isArchived, isPinned) VALUES ('$id', 'title-$id', 0, 0, 0, 0, 0)"
        )
    }

    private fun insertTask(id: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO project_records (recordId, parentProjectId, description, durationMillis, " +
                "startDateTimeEpochMs, isFinished, isTimerRunning) " +
                "VALUES ('$id', '$projectId', 'task-$id', 0, 0, 0, 0)"
        )
    }

    private fun insertInterval(id: String, taskId: String, duration: Long) {
        connection.execSQL(
            "INSERT INTO task_intervals (intervalId, parentTaskId, startDateTimeEpochMs, " +
                "endDateTimeEpochMs, durationMillis) VALUES ('$id', '$taskId', 0, $duration, $duration)"
        )
    }

    private fun queryStrings(sql: String): List<String> {
        val rows = mutableListOf<String>()
        val statement = connection.prepare(sql)
        try {
            while (statement.step()) rows += statement.getText(0)
        } finally {
            statement.close()
        }
        return rows
    }

    private fun countRows(sql: String): Int {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step()) statement.getLong(0).toInt() else 0
        } finally {
            statement.close()
        }
    }

    private fun parentProjectIdOf(intervalId: String): String? =
        queryStrings("SELECT parentProjectId FROM task_intervals WHERE intervalId = '$intervalId'")
            .firstOrNull()

    /** The whole point of the migration: existing intervals keep their data and gain a project. */
    @Test
    fun backfillsParentProjectIdFromTheOwningTask() {
        insertProject("p1")
        insertProject("p2")
        insertTask("t1", "p1")
        insertTask("t2", "p2")
        insertInterval("i1", "t1", duration = 60_000)
        insertInterval("i2", "t2", duration = 30_000)

        TrackyDatabase.MIGRATION_12_13.migrate(connection)

        assertEquals("p1", parentProjectIdOf("i1"))
        assertEquals("p2", parentProjectIdOf("i2"))
        // The tracked time itself must survive untouched — that is the data users care about.
        assertEquals(
            60_000,
            countRows("SELECT durationMillis FROM task_intervals WHERE intervalId = 'i1'")
        )
    }

    /** Intervals whose task was deleted pre-13 cannot satisfy the new FK, so they are dropped. */
    @Test
    fun dropsIntervalsWhoseParentTaskIsGone() {
        insertProject("p1")
        insertTask("t1", "p1")
        insertInterval("i1", "t1", duration = 60_000)
        insertInterval("i-orphan", "deleted-task", duration = 5_000)

        TrackyDatabase.MIGRATION_12_13.migrate(connection)

        assertEquals("p1", parentProjectIdOf("i1"))
        assertNull(parentProjectIdOf("i-orphan"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM task_intervals"))
    }

    /**
     * A task row can itself dangle: SQLite never validates rows that already exist when a
     * constraint is introduced, and FKs are off during every migration. Such an interval would
     * violate the new projects FK the moment enforcement came back on, so it goes too.
     */
    @Test
    fun dropsIntervalsWhoseParentProjectIsGone() {
        insertTask("ghost-task", "deleted-project")
        insertInterval("i-ghost", "ghost-task", duration = 5_000)

        TrackyDatabase.MIGRATION_12_13.migrate(connection)

        assertNull(parentProjectIdOf("i-ghost"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM task_intervals"))
    }

    /** Nothing may be left behind that would trip enforcement once Room turns FKs back on. */
    @Test
    fun leavesNoForeignKeyViolations() {
        insertProject("p1")
        insertTask("t1", "p1")
        insertInterval("i1", "t1", duration = 60_000)
        insertInterval("i-orphan", "deleted-task", duration = 5_000)
        insertTask("ghost-task", "deleted-project")
        insertInterval("i-ghost", "ghost-task", duration = 5_000)

        TrackyDatabase.MIGRATION_12_13.migrate(connection)
        connection.execSQL("DELETE FROM project_records WHERE parentProjectId = 'deleted-project'")

        assertTrue(queryStrings("PRAGMA foreign_key_check").isEmpty())
    }

    @Test
    fun createsAnIndexOnEachForeignKey() {
        TrackyDatabase.MIGRATION_12_13.migrate(connection)

        val indices = queryStrings(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'task_intervals'"
        )
        assertTrue("index_task_intervals_parentTaskId" in indices, "missing task index: $indices")
        assertTrue("index_task_intervals_parentProjectId" in indices, "missing project index: $indices")
    }
}
