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
 * Exercises [TrackyDatabase.MIGRATION_17_18], which adds a nullable `sortIndex` to `project_tasks`
 * and `project_sub_tasks` so a dragged order has somewhere to live.
 *
 * The migration is purely additive and deliberately backfills nothing: `sortedByTaskOrder` sorts
 * nulls last and falls back to `startDateTimeEpochMs`, so existing rows get a deterministic order
 * without the migration inventing indices. The two things worth asserting are therefore that the
 * columns arrive nullable, and that existing rows survive untouched — a rewrite here would be a
 * user's whole task tree.
 *
 * The connection is opened raw rather than through Room so the v17 schema can be built verbatim
 * from the exported `17.json`, exactly as [Migration15To16Test] does.
 */
class Migration17To18Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV17()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v17 DDL for the three tables this migration can reach, copied from
    // composeApp/schemas/…/17.json. The rest of the schema is irrelevant here.
    private fun createSchemaV17() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `projects` (`projectId` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                "`description` TEXT, `color` INTEGER, `totalDuration` INTEGER, " +
                "`startDateTimeEpochMs` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL, " +
                "`useLightTextColor` INTEGER NOT NULL, `endDateTimeEpochMs` INTEGER, " +
                "`isArchived` INTEGER NOT NULL, `trashedAtEpochMs` INTEGER, `isPinned` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, `sortIndex` INTEGER, PRIMARY KEY(`projectId`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_tasks` (`projectTaskId` TEXT NOT NULL, " +
                "`parentProjectId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, " +
                "`durationMillis` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`isTimerRunning` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER, " +
                "PRIMARY KEY(`projectTaskId`), " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_sub_tasks` (`projectSubTaskId` TEXT NOT NULL, " +
                "`parentProjectTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT, `durationMillis` INTEGER, " +
                "`isTimerRunning` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, PRIMARY KEY(`projectSubTaskId`), " +
                "FOREIGN KEY(`parentProjectTaskId`) REFERENCES `project_tasks`(`projectTaskId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`parentProjectId`) REFERENCES `projects`(`projectId`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
    }

    // ---- fixtures ------------------------------------------------------------------------------

    private fun seedV17() {
        connection.execSQL(
            "INSERT INTO projects (projectId, title, startDateTimeEpochMs, isFinished, " +
                "useLightTextColor, isArchived, isPinned) VALUES ('p1', 'project', 0, 0, 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO project_tasks (projectTaskId, parentProjectId, title, description, " +
                "durationMillis, startDateTimeEpochMs, endDateTimeEpochMs, isFinished, " +
                "isTimerRunning, updatedAtEpochMs) " +
                "VALUES ('t1', 'p1', 'Write the report', 'note', 90000, 1000, 91000, 0, 1, 4242)"
        )
        connection.execSQL(
            "INSERT INTO project_sub_tasks (projectSubTaskId, parentProjectTaskId, parentProjectId, " +
                "title, description, durationMillis, isTimerRunning, startDateTimeEpochMs, isFinished) " +
                "VALUES ('s1', 't1', 'p1', 'Draft it', 'sub note', 5000, 0, 2000, 0)"
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
    fun bothLevelsGainASortIndexColumn() {
        seedV17()

        TrackyDatabase.MIGRATION_17_18.migrate(connection)

        assertTrue(columnExists("project_tasks", "sortIndex"), "project_tasks should gain sortIndex")
        assertTrue(columnExists("project_sub_tasks", "sortIndex"), "project_sub_tasks should gain sortIndex")
    }

    @Test
    fun theNewColumnsAreNullableAndStartNull() {
        seedV17()

        TrackyDatabase.MIGRATION_17_18.migrate(connection)

        // Nullable is what makes the migration backfill-free: NULL means "never dragged", and the
        // sort rule falls back to creation order for those rows.
        assertEquals(
            0L,
            queryLong("SELECT \"notnull\" FROM pragma_table_info('project_tasks') WHERE name = 'sortIndex'")
        )
        assertEquals(
            0L,
            queryLong("SELECT \"notnull\" FROM pragma_table_info('project_sub_tasks') WHERE name = 'sortIndex'")
        )
        assertNull(queryLong("SELECT sortIndex FROM project_tasks WHERE projectTaskId = 't1'"))
        assertNull(queryLong("SELECT sortIndex FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
    }

    @Test
    fun existingRowsAreLeftUntouched() {
        seedV17()

        TrackyDatabase.MIGRATION_17_18.migrate(connection)

        assertEquals("Write the report", queryText("SELECT title FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(90000L, queryLong("SELECT durationMillis FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(4242L, queryLong("SELECT updatedAtEpochMs FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals("Draft it", queryText("SELECT title FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
        assertEquals(5000L, queryLong("SELECT durationMillis FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
    }

    @Test
    fun anIndexCanBeWrittenAfterTheMigration() {
        seedV17()

        TrackyDatabase.MIGRATION_17_18.migrate(connection)
        connection.execSQL("UPDATE project_tasks SET sortIndex = 3 WHERE projectTaskId = 't1'")
        connection.execSQL("UPDATE project_sub_tasks SET sortIndex = 7 WHERE projectSubTaskId = 's1'")

        assertEquals(3L, queryLong("SELECT sortIndex FROM project_tasks WHERE projectTaskId = 't1'"))
        assertEquals(7L, queryLong("SELECT sortIndex FROM project_sub_tasks WHERE projectSubTaskId = 's1'"))
    }
}
