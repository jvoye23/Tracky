package com.jvcs.tracky.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import java.nio.file.Files

/**
 * A `tracky_prefs`-shaped store backed by a fresh temp directory, one per call so tests never see
 * each other's keys.
 *
 * DataStore refuses a second active instance over the same file in one process, so a test that
 * wants to prove a value was persisted builds a new *consumer* over this same store rather than a
 * second store.
 */
fun createTestDataStore(): DataStore<Preferences> {
    val dir = Files.createTempDirectory("tracky-prefs-test").toFile().apply { deleteOnExit() }
    val file = dir.resolve("tracky_prefs.preferences_pb").absolutePath
    return PreferenceDataStoreFactory.createWithPath(produceFile = { file.toPath() })
}
