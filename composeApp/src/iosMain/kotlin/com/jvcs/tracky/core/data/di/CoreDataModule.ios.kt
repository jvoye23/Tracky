package com.jvcs.tracky.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.jvcs.tracky.core.data.sync.IosSyncScheduler
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import org.koin.dsl.bind
import org.koin.dsl.module

@OptIn(ExperimentalForeignApi::class)
actual val platformCoreDataModule = module {
    single { DatabaseFactory() }
    single { ConnectivityObserver() }
    single { AppLifecycleObserver() }
    single { IosSyncScheduler() } bind SyncScheduler::class
    single<HttpClientEngine> { Darwin.create() }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = {
                val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null
                )
                (documentDirectory!!.path + "/tracky_prefs.preferences_pb").toPath()
            }
        )
    }
}
