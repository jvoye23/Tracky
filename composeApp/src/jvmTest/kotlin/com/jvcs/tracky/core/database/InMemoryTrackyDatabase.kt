package com.jvcs.tracky.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** A real schema in memory: the data-source tests exercise Room's queries, not a fake of them. */
internal fun inMemoryTrackyDatabase(): TrackyDatabase =
    Room
        .inMemoryDatabaseBuilder<TrackyDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
