package com.jvcs.tracky.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [TrackyDatabase.MIGRATION_13_14] against a real SQLite connection.
 *
 * Unlike [Migration12To13Test] this migration touches no existing row: both tables are new, so the
 * risk is not data loss but the cascade wiring. A subtask hangs off its task *and* its project, and
 * a subtask interval additionally off the task interval that encloses it — three delete paths that
 * have to reach it, and none of which Room would report as broken until runtime.
 *
 * The connection is opened raw rather than through Room so the v13 schema can be built verbatim
 * from the exported `13.json`. Foreign keys are turned on explicitly *after* the migration, which
 * is what Room does too: the generated `onOpen` enables them only once the migration transaction
 * has committed.
 */
class Migration13To14Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV13()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v13 DDL, copied from composeApp/schemas/…/13.json so the fixture is provably the shape
    // real installs are migrating from.
    private fun createSchemaV13() {
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
                "`parentTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`startDateTimeEpochMs` INTEGER NOT NULL, `endDateTimeEpochMs` INTEGER, " +
                "`durationMillis` INTEGER NOT NULL, PRIMARY KEY(`intervalId`), " +
                "FOREIGN KEY(`parentTaskId`) REFERENCES `project_records`(`recordId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
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

    private fun insertInterval(id: String, taskId: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO task_intervals (intervalId, parentTaskId, parentProjectId, " +
                "startDateTimeEpochMs, endDateTimeEpochMs, durationMillis) " +
                "VALUES ('$id', '$taskId', '$projectId', 0, 60000, 60000)"
        )
    }

    private fun insertSubTask(id: String, taskId: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO project_sub_tasks (projectSubTaskId, parentProjectTaskId, parentProjectId, " +
                "title, durationMillis, isTimerRunning, startDateTimeEpochMs, isFinished) " +
                "VALUES ('$id', '$taskId', '$projectId', 'sub-$id', 0, 0, 0, 0)"
        )
    }

    private fun insertSubTaskInterval(
        id: String,
        subTaskId: String,
        taskIntervalId: String,
        projectId: String
    ) {
        connection.execSQL(
            "INSERT INTO sub_task_intervals (subTaskIntervalId, parentSubTaskId, " +
                "parentTaskIntervalId, parentProjectId, startDateTimeEpochMs, endDateTimeEpochMs, " +
                "durationMillis) VALUES ('$id', '$subTaskId', '$taskIntervalId', '$projectId', 0, 60000, 60000)"
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

    /** Seeds a project -> task -> interval -> subtask -> subtask interval chain, FKs enforced. */
    private fun migrateAndSeedFullChain() {
        TrackyDatabase.MIGRATION_13_14.migrate(connection)
        connection.execSQL("PRAGMA foreign_keys = ON")
        insertProject("p1")
        insertTask("t1", "p1")
        insertInterval("i1", "t1", "p1")
        insertSubTask("s1", "t1", "p1")
        insertSubTaskInterval("si1", "s1", "i1", "p1")
    }

    @Test
    fun createsBothTablesAndLeavesExistingDataAlone() {
        insertProject("p1")
        insertTask("t1", "p1")
        insertInterval("i1", "t1", "p1")

        TrackyDatabase.MIGRATION_13_14.migrate(connection)

        assertEquals(0, countRows("SELECT COUNT(*) FROM project_sub_tasks"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM sub_task_intervals"))
        // Nothing in the migration touches the pre-existing tree.
        assertEquals(1, countRows("SELECT COUNT(*) FROM projects"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM project_records"))
        assertEquals(
            60_000,
            countRows("SELECT durationMillis FROM task_intervals WHERE intervalId = 'i1'")
        )
    }

    /** Running it twice must be a no-op — a half-applied upgrade gets retried on next launch. */
    @Test
    fun isIdempotent() {
        TrackyDatabase.MIGRATION_13_14.migrate(connection)
        insertProject("p1")
        insertTask("t1", "p1")
        insertSubTask("s1", "t1", "p1")

        TrackyDatabase.MIGRATION_13_14.migrate(connection)

        assertEquals(1, countRows("SELECT COUNT(*) FROM project_sub_tasks"))
    }

    @Test
    fun deletingATaskCascadesToItsSubTasksAndTheirIntervals() {
        migrateAndSeedFullChain()

        connection.execSQL("DELETE FROM project_records WHERE recordId = 't1'")

        assertEquals(0, countRows("SELECT COUNT(*) FROM project_sub_tasks"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM sub_task_intervals"))
    }

    @Test
    fun deletingAProjectCascadesToItsSubTasksAndTheirIntervals() {
        migrateAndSeedFullChain()

        connection.execSQL("DELETE FROM projects WHERE projectId = 'p1'")

        assertEquals(0, countRows("SELECT COUNT(*) FROM project_sub_tasks"))
        assertEquals(0, countRows("SELECT COUNT(*) FROM sub_task_intervals"))
    }

    /**
     * The nesting invariant: a subtask interval lives inside one task interval, so deleting the
     * enclosing interval takes it with it — but leaves the subtask itself, which is still a unit of
     * work, alone.
     */
    @Test
    fun deletingATaskIntervalCascadesToTheSubTaskIntervalsInsideIt() {
        migrateAndSeedFullChain()

        connection.execSQL("DELETE FROM task_intervals WHERE intervalId = 'i1'")

        assertEquals(0, countRows("SELECT COUNT(*) FROM sub_task_intervals"))
        assertEquals(1, countRows("SELECT COUNT(*) FROM project_sub_tasks"))
    }

    @Test
    fun leavesNoForeignKeyViolations() {
        migrateAndSeedFullChain()

        assertTrue(queryStrings("PRAGMA foreign_key_check").isEmpty())
    }

    @Test
    fun createsAnIndexOnEachForeignKey() {
        TrackyDatabase.MIGRATION_13_14.migrate(connection)

        val expected = mapOf(
            "project_sub_tasks" to listOf("parentProjectTaskId", "parentProjectId"),
            "sub_task_intervals" to
                listOf("parentSubTaskId", "parentTaskIntervalId", "parentProjectId")
        )
        expected.forEach { (table, columns) ->
            val actual = queryStrings(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = '$table'"
            )
            columns.forEach { column ->
                assertTrue("index_${table}_$column" in actual, "missing $column index: $actual")
            }
        }
    }
}
