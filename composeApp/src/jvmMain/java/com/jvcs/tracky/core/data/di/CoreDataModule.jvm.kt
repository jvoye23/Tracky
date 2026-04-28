package com.jvcs.tracky.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.jvcs.tracky.core.database.DatabaseFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path.Companion.toPath
import org.koin.dsl.module

actual val platformCoreDataModule = module {
    single { DatabaseFactory() }
    single<HttpClientEngine> { OkHttp.create() }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                "tracky_prefs.preferences_pb".toPath()
            }
        )
    }
}
