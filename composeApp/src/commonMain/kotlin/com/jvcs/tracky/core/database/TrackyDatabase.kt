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
    version = 4,
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
    }
}