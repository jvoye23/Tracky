package com.jvcs.tracky.core.database

import androidx.room.RoomDatabaseConstructor

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object TrackyDatabaseConstructor: RoomDatabaseConstructor<TrackyDatabase> {
    override fun initialize(): TrackyDatabase
}