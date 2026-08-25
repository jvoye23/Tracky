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
 * Exercises [TrackyDatabase.MIGRATION_15_16], which renames `project_records` to `project_tasks`,
 * renames its primary key to `projectTaskId`, and splits the overloaded `description` column into a
 * real `title` plus a nullable `description`.
 *
 * Two things here can only fail at runtime, so both get their own test. The first is the backfill:
 * `description` was the only place a task's title was ever stored, so migrating it into `title` has
 * to be exact — a blank title is an invisible task. The second is the foreign keys. SQLite rewrites
 * a child's REFERENCES clause on a table rename only when `PRAGMA foreign_keys` is ON, and Room
 * migrates with them OFF, so `task_intervals` and `project_sub_tasks` are rebuilt by hand. If that
 * rebuild were dropped, both would keep pointing at a `project_records` that no longer exists and
 * the cascade would silently stop reaching them.
 *
 * The connection is opened raw rather than through Room so the v15 schema can be built verbatim
 * from the exported `15.json`. Foreign keys are enabled only *after* the migration, exactly as
 * Room's generated `onOpen` does.
 */
class Migration15To16Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV15()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v15 DDL, copied from composeApp/schemas/…/15.json so the fixture is provably the shape
    // real installs are migrating from. pending_sync_operations is left out: nothing touches it.
    private fun createSchemaV15() {
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
            "CREATE INDEX IF NOT EXISTS `index_project_records_parentProjectId` " +
                "ON `project_records` (`parentProjectId`)"
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
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_intervals_parentTaskId` " +
                "ON `task_intervals` (`parentTaskId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_task_intervals_parentProjectId` " +
                "ON `task_intervals` (`parentProjectId`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_sub_tasks` (`projectSubTaskId` TEXT NOT NULL, " +
                "`parentProjectTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT, `durationMillis` INTEGER, " +
                "`isTimerRunning` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, PRIMARY KEY(`projectSubTaskId`), " +
                "FOREIGN KEY(`parentProjectTaskId`) REFERENCES `project_records`(`recordId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_project_sub_tasks_parentProjectTaskId` " +
                "ON `project_sub_tasks` (`parentProjectTaskId`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_project_sub_tasks_parentProjectId` " +
                "ON `project_sub_tasks` (`parentProjectId`)"
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
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private fun insertProject(id: String) {
        connection.execSQL(
            "INSERT INTO projects (projectId, title, startDateTimeEpochMs, isFinished, " +
                "useLightTextColor, isArchived, isPinned) VALUES ('$id', 'project-$id', 0, 0, 0, 0, 0)"
        )
    }

    /** v15 has no title column, so the task's title goes where it always went: description. */
    private fun insertTask(id: String, projectId: String, title: String, updatedAt: Long?) {
        connection.execSQL(
            "INSERT INTO project_records (recordId, parentProjectId, description, durationMillis, " +
                "startDateTimeEpochMs, endDateTimeEpochMs, isFinished, isTimerRunning, updatedAtEpochMs) " +
                "VALUES ('$id', '$projectId', '$title', 90000, 1000, 91000, 0, 1, ${updatedAt ?: "NULL"})"
        )
    }

    private fun insertInterval(id: String, taskId: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO task_intervals (intervalId, parentTaskId, parentProjectId, " +
                "startDateTimeEpochMs, endDateTimeEpochMs, durationMillis) " +
                "VALUES ('$id', '$taskId', '$projectId', 1000, 91000, 90000)"
        )
    }

    private fun insertSubTask(id: String, taskId: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO project_sub_tasks (projectSubTaskId, parentProjectTaskId, parentProjectId, " +
                "title, description, durationMillis, isTimerRunning, startDateTimeEpochMs, isFinished) " +
                "VALUES ('$id', '$taskId', '$projectId', 'sub-$id', 'note-$id', 5000, 0, 0, 0)"
        )
    }

    private fun insertSubTaskInterval(id: String, subTaskId: String, taskIntervalId: String, projectId: String) {
        connection.execSQL(
            "INSERT INTO sub_task_intervals (subTaskIntervalId, parentSubTaskId, " +
                "parentTaskIntervalId, parentProjectId, startDateTimeEpochMs, endDateTimeEpochMs, " +
                "durationMillis, startedParentTimer) " +
                "VALUES ('$id', '$subTaskId', '$taskIntervalId', '$projectId', 0, 5000, 5000, 1)"
        )
    }

    private fun seedV15() {
        insertProject("p1")
        insertTask("t1", "p1", "Write the report", 4242L)
        insertInterval("i1", "t1", "p1")
        insertSubTask("s1", "t1", "p1")
        insertSubTaskInterval("si1", "s1", "i1", "p1")
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

    private fun countRows(sql: String): Int = queryLong(sql)?.toInt() ?: 0

    private fun tableExists(name: String): Boolean =
        countRows("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = '$name'") == 1

    // ---- tests ---------------------------------------------------------------------------------

    @Test
    fun theTableIsRenamedAndTheOldOneIsGone() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        assertTrue(tableExists("project_tasks"), "project_tasks should exist after the migration")
        assertTrue(!tableExists("project_records"), "project_records should be gone")
        assertEquals(
            listOf("projectTaskId"),
            queryStrings("SELECT name FROM pragma_table_info('project_tasks') WHERE pk = 1"),
            "the primary key should have been renamed to projectTaskId"
        )
    }

    @Test
    fun theTitleIsBackfilledFromTheDescriptionItUsedToShare() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        assertEquals(
            "Write the report",
            queryText("SELECT title FROM project_tasks WHERE projectTaskId = 't1'")
        )
        // description held nothing but the title, so it starts the new schema empty rather than
        // carrying a duplicate the user never wrote.
        assertNull(queryText("SELECT description FROM project_tasks WHERE projectTaskId = 't1'"))
    }

    @Test
    fun titleIsNotNullAndDescriptionIsNullable() {
        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        assertEquals(
            1L,
            queryLong("SELECT \"notnull\" FROM pragma_table_info('project_tasks') WHERE name = 'title'"),
            "a task without a title would be invisible in the list"
        )
        assertEquals(
            0L,
            queryLong("SELECT \"notnull\" FROM pragma_table_info('project_tasks') WHERE name = 'description'")
        )
    }

    @Test
    fun theTrackedTimeAndTimerStateSurvive() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        assertEquals(90_000L, queryLong("SELECT durationMillis FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(1_000L, queryLong("SELECT startDateTimeEpochMs FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(91_000L, queryLong("SELECT endDateTimeEpochMs FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(1L, queryLong("SELECT isTimerRunning FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(4_242L, queryLong("SELECT updatedAtEpochMs FROM project_tasks WHERE projectTaskId = 't1'"))
    }

    @Test
    fun everyRowInTheRebuiltChildTablesSurvives() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        assertEquals(1, countRows("SELECT count(*) FROM task_intervals"))
        assertEquals(90_000L, queryLong("SELECT durationMillis FROM task_intervals WHERE intervalId = 'i1'"))
        assertEquals(1, countRows("SELECT count(*) FROM project_sub_tasks"))
        assertEquals("sub-s1", queryText("SELECT title FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
        assertEquals("note-s1", queryText("SELECT description FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
        // sub_task_intervals is not rebuilt at all, so its local-only flag has to be untouched.
        assertEquals(1, countRows("SELECT count(*) FROM sub_task_intervals"))
        assertEquals(
            1L,
            queryLong("SELECT startedParentTimer FROM sub_task_intervals WHERE subTaskIntervalId = 'si1'")
        )
    }

    @Test
    fun theChildForeignKeysNowNameProjectTasks() {
        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        // This is the assertion that catches a bare ALTER TABLE ... RENAME TO: with foreign keys
        // off, SQLite leaves these clauses pointing at the old table name.
        assertTrue(
            queryStrings("SELECT \"table\" FROM pragma_foreign_key_list('task_intervals')")
                .contains("project_tasks"),
            "task_intervals should cascade from project_tasks"
        )
        assertTrue(
            queryStrings("SELECT \"table\" FROM pragma_foreign_key_list('project_sub_tasks')")
                .contains("project_tasks"),
            "project_sub_tasks should cascade from project_tasks"
        )
        assertTrue(
            queryStrings("SELECT \"table\" FROM pragma_foreign_key_list('task_intervals')")
                .none { it == "project_records" },
            "no foreign key should still name project_records"
        )
    }

    @Test
    fun deletingAProjectStillReachesTheWholeTree() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("DELETE FROM projects WHERE projectId = 'p1'")

        assertEquals(0, countRows("SELECT count(*) FROM project_tasks"))
        assertEquals(0, countRows("SELECT count(*) FROM task_intervals"))
        assertEquals(0, countRows("SELECT count(*) FROM project_sub_tasks"))
        assertEquals(0, countRows("SELECT count(*) FROM sub_task_intervals"))
    }

    @Test
    fun deletingATaskStillReachesItsIntervalsAndSubtasks() {
        seedV15()

        TrackyDatabase.MIGRATION_15_16.migrate(connection)
        connection.execSQL("PRAGMA foreign_keys = ON")
        connection.execSQL("DELETE FROM project_tasks WHERE projectTaskId = 't1'")

        assertEquals(0, countRows("SELECT count(*) FROM task_intervals"))
        assertEquals(0, countRows("SELECT count(*) FROM project_sub_tasks"))
        assertEquals(0, countRows("SELECT count(*) FROM sub_task_intervals"))
        // The project itself is not a child of anything here and must stay.
        assertEquals(1, countRows("SELECT count(*) FROM projects"))
    }

    @Test
    fun everyIndexIsBackUnderItsNewTable() {
        TrackyDatabase.MIGRATION_15_16.migrate(connection)

        val indices = queryStrings("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_%'")
        assertTrue(indices.contains("index_project_tasks_parentProjectId"), "got: $indices")
        assertTrue(indices.contains("index_task_intervals_parentTaskId"), "got: $indices")
        assertTrue(indices.contains("index_task_intervals_parentProjectId"), "got: $indices")
        assertTrue(indices.contains("index_project_sub_tasks_parentProjectTaskId"), "got: $indices")
        assertTrue(indices.contains("index_project_sub_tasks_parentProjectId"), "got: $indices")
        assertTrue(
            indices.none { it.contains("project_records") },
            "the old index name should not survive the rename: $indices"
        )
    }
}
