package com.jvcs.tracky.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jvcs.tracky.core.database.dao.ProjectDao
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity

@Database(
    entities = [
        ProjectEntity::class,
        ProjectSessionEntity::class
    ],
    version = 1,
)
@TypeConverters(RoomConverters::class)
@ConstructedBy(TrackyDatabaseConstructor::class)
abstract class TrackyDatabase: RoomDatabase() {
    abstract val projectDao: ProjectDao

    companion object {
        const val DB_NAME = "tracky.db"
    }
}