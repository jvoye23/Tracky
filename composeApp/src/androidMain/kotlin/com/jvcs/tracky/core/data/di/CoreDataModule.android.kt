package com.jvcs.tracky.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.jvcs.tracky.core.data.sync.AndroidSyncScheduler
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformCoreDataModule = module {
    single { DatabaseFactory(androidContext()) }
    single { ConnectivityObserver(androidContext()) }
    single { AppLifecycleObserver() }
    single { AndroidSyncScheduler(androidContext()) } bind SyncScheduler::class
    single<HttpClientEngine> { OkHttp.create() }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                androidContext().filesDir.resolve("tracky_prefs.preferences_pb").absolutePath.toPath()
            }
        )
    }
}
