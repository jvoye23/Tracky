package com.jvcs.tracky.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Exercises [TrackyDatabase.MIGRATION_14_15], which adds `startedParentTimer`.
 *
 * The default matters more than the column does: every subtask interval written before v15 was
 * created by a task-level start, so none of them opened their own parent. Defaulting them to 1
 * would make stopping any pre-existing subtask silently stop its task too.
 */
class Migration14To15Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
        createSchemaV14()
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    // The v14 DDL for the two subtask tables, copied from composeApp/schemas/…/14.json. The tables
    // this migration does not touch are left out.
    private fun createSchemaV14() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `project_sub_tasks` (`projectSubTaskId` TEXT NOT NULL, " +
                "`parentProjectTaskId` TEXT NOT NULL, `parentProjectId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `description` TEXT, `durationMillis` INTEGER, " +
                "`isTimerRunning` INTEGER NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `isFinished` INTEGER NOT NULL, " +
                "`updatedAtEpochMs` INTEGER, PRIMARY KEY(`projectSubTaskId`))"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `sub_task_intervals` (`subTaskIntervalId` TEXT NOT NULL, " +
                "`parentSubTaskId` TEXT NOT NULL, `parentTaskIntervalId` TEXT NOT NULL, " +
                "`parentProjectId` TEXT NOT NULL, `startDateTimeEpochMs` INTEGER NOT NULL, " +
                "`endDateTimeEpochMs` INTEGER, `durationMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`subTaskIntervalId`))"
        )
    }

    private fun queryLong(sql: String): Long? {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step()) statement.getLong(0) else null
        } finally {
            statement.close()
        }
    }

    @Test
    fun existingIntervalsDidNotStartTheirParentTimer() {
        connection.execSQL(
            "INSERT INTO sub_task_intervals (subTaskIntervalId, parentSubTaskId, " +
                "parentTaskIntervalId, parentProjectId, startDateTimeEpochMs, endDateTimeEpochMs, " +
                "durationMillis) VALUES ('si1', 's1', 'i1', 'p1', 0, 60000, 60000)"
        )

        TrackyDatabase.MIGRATION_14_15.migrate(connection)

        assertEquals(
            0L,
            queryLong("SELECT startedParentTimer FROM sub_task_intervals WHERE subTaskIntervalId = 'si1'")
        )
        // The tracked time itself must survive untouched.
        assertEquals(
            60_000L,
            queryLong("SELECT durationMillis FROM sub_task_intervals WHERE subTaskIntervalId = 'si1'")
        )
    }

    @Test
    fun theColumnIsNotNullSoEveryRowHasAnAnswer() {
        TrackyDatabase.MIGRATION_14_15.migrate(connection)

        val notNull = queryLong(
            "SELECT \"notnull\" FROM pragma_table_info('sub_task_intervals') " +
                "WHERE name = 'startedParentTimer'"
        )
        assertEquals(1L, notNull)
    }
}
