package com.jvcs.tracky.core.database

/** A real schema in memory: the data-source tests exercise Room's queries, not a fake of them. */
internal expect fun inMemoryTrackyDatabase(): TrackyDatabase
