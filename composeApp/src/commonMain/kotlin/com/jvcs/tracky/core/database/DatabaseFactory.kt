package com.jvcs.tracky.core.database

import androidx.room.RoomDatabase

expect class DatabaseFactory {
    fun create(): RoomDatabase.Builder<TrackyDatabase>
}