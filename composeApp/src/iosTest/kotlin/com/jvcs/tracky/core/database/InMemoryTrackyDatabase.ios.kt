package com.jvcs.tracky.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual fun inMemoryTrackyDatabase(): TrackyDatabase =
    Room
        .inMemoryDatabaseBuilder<TrackyDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
