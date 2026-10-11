package com.jvcs.tracky.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.jvcs.tracky.core.data.identity.InMemoryPreferencesDataStore

/**
 * A fresh `tracky_prefs`-shaped store per call, so tests never see each other's keys.
 *
 * In memory rather than on disk: the tests run on the iOS simulator, and what they prove is what a
 * store keeps, not how DataStore writes its file. A test that wants to prove a value was persisted
 * builds a new *consumer* over this same store, exactly as it would over a file.
 */
fun createTestDataStore(): DataStore<Preferences> = InMemoryPreferencesDataStore()
