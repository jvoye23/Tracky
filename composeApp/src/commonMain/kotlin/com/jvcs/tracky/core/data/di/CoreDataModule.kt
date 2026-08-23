package com.jvcs.tracky.core.data.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.data.auth.DataStoreSessionStorage
import com.jvcs.tracky.core.data.auth.KtorAuthService
import com.jvcs.tracky.core.data.networking.HttpClientFactory
import com.jvcs.tracky.core.data.sync.RoomPendingSyncDataSource
import com.jvcs.tracky.core.data.sync.SyncCoordinator
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.util.SystemTimeProvider
import com.jvcs.tracky.core.domain.util.TimeProvider
import com.jvcs.tracky.features.project.data.interval.KtorRemoteIntervalDataSource
import com.jvcs.tracky.features.project.data.interval.OfflineFirstIntervalRepository
import com.jvcs.tracky.features.project.data.interval.RoomLocalIntervalDataSource
import com.jvcs.tracky.features.project.data.project.KtorRemoteProjectDataSource
import com.jvcs.tracky.features.project.data.project.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project.data.project.RoomLocalProjectDataSource
import com.jvcs.tracky.features.project.data.task.KtorRemoteTaskDataSource
import com.jvcs.tracky.features.project.data.subtask.OfflineFirstSubTaskRepository
import com.jvcs.tracky.features.project.data.subtask.RoomLocalSubTaskDataSource
import com.jvcs.tracky.features.project.data.task.OfflineFirstTaskRepository
import com.jvcs.tracky.features.project.data.task.RoomLocalTaskDataSource
import com.jvcs.tracky.features.project.domain.interval.IntervalRepository
import com.jvcs.tracky.features.project.domain.interval.LocalIntervalDataSource
import com.jvcs.tracky.features.project.domain.interval.RemoteIntervalDataSource
import com.jvcs.tracky.features.project.domain.project.LocalProjectDataSource
import com.jvcs.tracky.features.project.domain.project.ProjectRepository
import com.jvcs.tracky.features.project.domain.project.RemoteProjectDataSource
import com.jvcs.tracky.features.project.domain.subtask.LocalSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtask.SubTaskRepository
import com.jvcs.tracky.features.project.domain.task.LocalTaskDataSource
import com.jvcs.tracky.features.project.domain.task.ProjectTaskRepository
import com.jvcs.tracky.features.project.domain.task.RemoteTaskDataSource
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformCoreDataModule: Module

val coreDataModule = module {
    includes(platformCoreDataModule)

    // The one place the app commits to a real clock — everything else takes the TimeProvider
    // interface so tests can pin time.
    single<TimeProvider> { SystemTimeProvider }

    single { get<TrackyDatabase>().projectDao }
    single { get<TrackyDatabase>().pendingSyncDao }

    singleOf(::RoomPendingSyncDataSource) bind PendingSyncDataSource::class

    singleOf(::RoomLocalProjectDataSource) bind LocalProjectDataSource::class
    singleOf(::KtorRemoteProjectDataSource) bind RemoteProjectDataSource::class
    singleOf(::RoomLocalTaskDataSource) bind LocalTaskDataSource::class
    singleOf(::RoomLocalSubTaskDataSource) bind LocalSubTaskDataSource::class
    singleOf(::KtorRemoteTaskDataSource) bind RemoteTaskDataSource::class
    singleOf(::RoomLocalIntervalDataSource) bind LocalIntervalDataSource::class
    singleOf(::KtorRemoteIntervalDataSource) bind RemoteIntervalDataSource::class

    single {
        OfflineFirstProjectRepository(
            localProjectDataSource = get(),
            remoteProjectDataSource = get(),
            pendingSyncDataSource = get(),
            syncScheduler = get(),
            applicationScope = get(qualifier = named("AppScope")),
            timeProvider = get()
        )
    } bind ProjectRepository::class

    // Intervals before tasks: the task repository pushes the timer's interval through this one.
    // No cycle — the interval repository reads tasks through LocalTaskDataSource, not through the
    // task repository.
    single {
        OfflineFirstIntervalRepository(
            localIntervalDataSource = get(),
            remoteIntervalDataSource = get(),
            localTaskDataSource = get(),
            pendingSyncDataSource = get(),
            syncScheduler = get(),
            applicationScope = get(qualifier = named("AppScope")),
            timeProvider = get()
        )
    } bind IntervalRepository::class

    single {
        OfflineFirstTaskRepository(
            localTaskDataSource = get(),
            remoteTaskDataSource = get(),
            pendingSyncDataSource = get(),
            syncScheduler = get(),
            intervalRepository = get(),
            timeProvider = get(),
            applicationScope = get(qualifier = named("AppScope"))
        )
    } bind ProjectTaskRepository::class

    // Subtasks after tasks: a subtask timer pushes the task interval it opened through the interval
    // repository and the changed task row through the task repository. No cycle — neither of those
    // knows subtasks exist.
    single {
        OfflineFirstSubTaskRepository(
            localSubTaskDataSource = get(),
            localTaskDataSource = get(),
            intervalRepository = get(),
            projectTaskRepository = get(),
            timeProvider = get()
        )
    } bind SubTaskRepository::class

    // The one place the projects → tasks → intervals sync order is expressed.
    singleOf(::SyncCoordinator) bind SyncRepository::class

    single(createdAtStart = true) {
        ProjectSyncManager(
            connectivityObserver = get(),
            appLifecycleObserver = get(),
            syncRepository = get(),
            projectRepository = get(),
            applicationScope = get(qualifier = named("AppScope")),
            timeProvider = get(),
        )
    }

    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
    single {
        get<DatabaseFactory>()
            .create()
            .addMigrations(
                TrackyDatabase.MIGRATION_1_2,
                TrackyDatabase.MIGRATION_2_3,
                TrackyDatabase.MIGRATION_3_4,
                TrackyDatabase.MIGRATION_4_5,
                TrackyDatabase.MIGRATION_5_6,
                TrackyDatabase.MIGRATION_6_7,
                TrackyDatabase.MIGRATION_7_8,
                TrackyDatabase.MIGRATION_8_9,
                TrackyDatabase.MIGRATION_9_10,
                TrackyDatabase.MIGRATION_10_11,
                TrackyDatabase.MIGRATION_11_12,
                TrackyDatabase.MIGRATION_12_13,
                TrackyDatabase.MIGRATION_13_14,
                TrackyDatabase.MIGRATION_14_15,
            )
            .setDriver(BundledSQLiteDriver())
            // Single connection (no WAL reader pool). The reactive sync (ProjectSyncManager) does
            // bulk writes on AppScope concurrently with the timer's interval writes; the bundled
            // driver's multi-connection WAL pool corrupts the file under that load (SQLITE_NOTADB).
            // TRUNCATE forces one connection so Room serializes all access.

            //.setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

            .build()
    }

    // Auth
    singleOf(::DataStoreSessionStorage) bind SessionStorage::class
    single { HttpClientFactory(get()).create(get()) }
    singleOf(::KtorAuthService) bind AuthService::class
    single { SocialAuthProvider() }
}
