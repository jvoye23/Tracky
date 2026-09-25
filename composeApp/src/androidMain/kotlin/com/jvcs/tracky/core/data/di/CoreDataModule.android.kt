package com.jvcs.tracky.core.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.jvcs.tracky.core.data.connectivity.AndroidConnectivityObserver
import com.jvcs.tracky.core.data.lifecycle.AndroidAppLifecycleObserver
import com.jvcs.tracky.core.data.notification.AndroidTimerNotificationController
import com.jvcs.tracky.core.data.notification.AndroidTimerNotificationPermissionRequester
import com.jvcs.tracky.core.data.sync.AndroidSyncScheduler
import com.jvcs.tracky.core.data.sync.AndroidTrashCleanupScheduler
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.domain.connectivity.ConnectivityObserver
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import com.jvcs.tracky.core.domain.notification.TimerNotificationController
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import com.jvcs.tracky.core.domain.sync.SyncScheduler
import com.jvcs.tracky.core.domain.sync.TrashCleanupScheduler
import com.jvcs.tracky.core.presentation.pdf.AndroidPdfDocumentWriter
import com.jvcs.tracky.core.presentation.pdf.PdfDocumentWriter
import com.jvcs.tracky.features.project.data.export.FileProviderExportFileSharer
import com.jvcs.tracky.features.project.domain.export.ExportFileSharer
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformCoreDataModule =
    module {
        single { DatabaseFactory(androidContext()) }
        single<ConnectivityObserver> { AndroidConnectivityObserver(androidContext()) }
        singleOf(::AndroidAppLifecycleObserver) bind AppLifecycleObserver::class

        single { AndroidSyncScheduler(androidContext()) } bind SyncScheduler::class
        single { AndroidTrashCleanupScheduler(androidContext()) } bind TrashCleanupScheduler::class

        single { AndroidTimerNotificationController(androidContext()) } bind TimerNotificationController::class
        single {
            AndroidTimerNotificationPermissionRequester(
                applicationContext = androidContext(),
                notificationController = get(),
            )
        } bind TimerNotificationPermissionRequester::class

        singleOf(::AndroidPdfDocumentWriter) bind PdfDocumentWriter::class
        single { FileProviderExportFileSharer(androidContext()) } bind ExportFileSharer::class

        single<HttpClientEngine> { OkHttp.create() }
        single<DataStore<Preferences>> {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    androidContext()
                        .filesDir
                        .resolve("tracky_prefs.preferences_pb")
                        .absolutePath
                        .toPath()
                },
            )
        }
    }
