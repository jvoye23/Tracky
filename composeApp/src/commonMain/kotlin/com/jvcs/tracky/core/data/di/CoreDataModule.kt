package com.jvcs.tracky.core.data.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.data.auth.DataStoreSessionStorage
import com.jvcs.tracky.core.data.auth.KtorAuthService
import com.jvcs.tracky.core.data.device.DataStoreDeviceIdProvider
import com.jvcs.tracky.core.data.networking.HttpClientFactory
import com.jvcs.tracky.core.data.sync.DataStoreServerClockOffsetStore
import com.jvcs.tracky.core.data.timer.KtorRemoteActiveTimerDataSource
import com.jvcs.tracky.core.data.timer.OfflineFirstActiveTimerRepository
import com.jvcs.tracky.core.domain.timer.ActiveTimerRepository
import com.jvcs.tracky.core.domain.timer.RemoteActiveTimerDataSource
import com.jvcs.tracky.core.data.sync.DataStoreSyncCursorStore
import com.jvcs.tracky.core.data.sync.KtorRemoteSyncDataSource
import com.jvcs.tracky.core.data.sync.RoomPendingSyncDataSource
import com.jvcs.tracky.core.data.sync.SyncCoordinator
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.device.DeviceIdProvider
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.core.domain.sync.PendingSyncDataSource
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.startup.StartupReconciliation
import com.jvcs.tracky.core.domain.sync.ProjectSyncManager
import com.jvcs.tracky.features.project.data.timer.OfflineFirstRunningTimerRepository
import com.jvcs.tracky.features.project.data.timer.OfflineFirstStrandedTimerRepository
import com.jvcs.tracky.features.project.data.timer.StrandedTimerReconciler
import com.jvcs.tracky.features.project.domain.timer.RunningTimerRepository
import com.jvcs.tracky.features.project.domain.timer.StrandedTimerRepository
import com.jvcs.tracky.core.domain.sync.DeltaSyncApplier
import com.jvcs.tracky.core.domain.sync.RemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.SyncCursorStore
import com.jvcs.tracky.core.domain.sync.SyncRecency
import com.jvcs.tracky.core.domain.sync.SyncRepository
import com.jvcs.tracky.core.domain.util.ServerClock
import com.jvcs.tracky.core.domain.util.ServerClockOffsetStore
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
import com.jvcs.tracky.features.project.data.subtask.KtorRemoteSubTaskDataSource
import com.jvcs.tracky.features.project.data.subtaskinterval.KtorRemoteSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.data.subtaskinterval.OfflineFirstSubTaskIntervalRepository
import com.jvcs.tracky.features.project.data.subtaskinterval.RoomLocalSubTaskIntervalDataSource
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
import com.jvcs.tracky.features.project.domain.subtask.RemoteSubTaskDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.LocalSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.RemoteSubTaskIntervalDataSource
import com.jvcs.tracky.features.project.domain.subtaskinterval.SubTaskIntervalRepository
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
    singleOf(::KtorRemoteSubTaskDataSource) bind RemoteSubTaskDataSource::class
    singleOf(::RoomLocalSubTaskIntervalDataSource) bind LocalSubTaskIntervalDataSource::class
    singleOf(::KtorRemoteSubTaskIntervalDataSource) bind RemoteSubTaskIntervalDataSource::class
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
            activeTimerRepository = get(),
            deviceIdProvider = get(),
            serverClock = get(),
            timeProvider = get(),
            applicationScope = get(qualifier = named("AppScope")),
            startupReconciliation = get()
        )
    } bind ProjectTaskRepository::class

    // Subtask intervals before subtasks, for the same reason intervals come before tasks: the
    // subtask timer pushes the interval it opened through this one. No cycle — this repository
    // reads subtasks through LocalSubTaskDataSource, not through SubTaskRepository.
    single {
        OfflineFirstSubTaskIntervalRepository(
            localSubTaskIntervalDataSource = get(),
            remoteSubTaskIntervalDataSource = get(),
            localSubTaskDataSource = get(),
            pendingSyncDataSource = get(),
            syncScheduler = get(),
            applicationScope = get(qualifier = named("AppScope")),
            timeProvider = get()
        )
    } bind SubTaskIntervalRepository::class

    // Subtasks after tasks: a subtask timer pushes the task interval it opened through the interval
    // repository and the changed task row through the task repository. No cycle — neither of those
    // knows subtasks exist.
    single {
        OfflineFirstSubTaskRepository(
            localSubTaskDataSource = get(),
            remoteSubTaskDataSource = get(),
            localTaskDataSource = get(),
            intervalRepository = get(),
            subTaskIntervalRepository = get(),
            projectTaskRepository = get(),
            activeTimerRepository = get(),
            deviceIdProvider = get(),
            serverClock = get(),
            pendingSyncDataSource = get(),
            syncScheduler = get(),
            applicationScope = get(qualifier = named("AppScope")),
            timeProvider = get(),
            startupReconciliation = get()
        )
    } bind SubTaskRepository::class

    // After the four repositories it pushes through. Nothing depends on this one but the review
    // dialog, so it closes no cycle.
    single {
        OfflineFirstStrandedTimerRepository(
            projectDao = get(),
            intervalRepository = get(),
            subTaskIntervalRepository = get(),
            projectTaskRepository = get(),
            subTaskRepository = get()
        )
    } bind StrandedTimerRepository::class

    // The read-only counterpart to the parked-timer repository above: same join, opposite filter.
    single {
        OfflineFirstRunningTimerRepository(projectDao = get(), deviceIdProvider = get())
    } bind RunningTimerRepository::class

    // The one place the projects → tasks → intervals → subtasks → subtask intervals sync order
    // is expressed.
    singleOf(::SyncCoordinator) bind SyncRepository::class

    // Before the repositories that await it. createdAtStart so the pass is running by the time the
    // first screen composes, rather than on first timer start.
    single(createdAtStart = true) {
        StrandedTimerReconciler(
            projectDao = get(),
            serverClock = get(),
            deviceIdProvider = get(),
            applicationScope = get(qualifier = named("AppScope"))
        )
    } bind StartupReconciliation::class

    single(createdAtStart = true) {
        ProjectSyncManager(
            connectivityObserver = get(),
            appLifecycleObserver = get(),
            syncRepository = get(),
            deltaSyncApplier = get(),
            applicationScope = get(qualifier = named("AppScope")),
        )
    }

    // createdAtStart so a timer left running from a previous session puts its notification back up
    // without waiting for a screen to compose. It gates itself on the stranded-timer pass.
    single(createdAtStart = true) {
        TimerNotificationCoordinator(
            runningTimerRepository = get(),
            projectTaskRepository = get(),
            subTaskRepository = get(),
            controller = get(),
            startupReconciliation = get(),
            serverClock = get(),
            applicationScope = get(qualifier = named("AppScope"))
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
                TrackyDatabase.MIGRATION_15_16,
                TrackyDatabase.MIGRATION_16_17,
                TrackyDatabase.MIGRATION_17_18,
                TrackyDatabase.MIGRATION_18_19,
            )
            .setDriver(BundledSQLiteDriver())
            // Single connection (no WAL reader pool). The reactive sync (ProjectSyncManager) does
            // bulk writes on AppScope concurrently with the timer's interval writes; the bundled
            // driver's multi-connection WAL pool corrupts the file under that load (SQLITE_NOTADB).
            // TRUNCATE forces one connection so Room serializes all access.

            //.setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

            .build()
    }

    // Device identity. Not createdAtStart: it is read from coroutines that already exist, and
    // minting it eagerly would touch DataStore on the startup path for no benefit.
    singleOf(::DataStoreDeviceIdProvider) bind DeviceIdProvider::class

    // How far this device has read the server's change feed. Cleared on logout.
    singleOf(::DataStoreSyncCursorStore) bind SyncCursorStore::class
    singleOf(::KtorRemoteSyncDataSource) bind RemoteSyncDataSource::class
    single {
        DeltaSyncApplier(
            remoteSyncDataSource = get(),
            localProjectDataSource = get(),
            projectRepository = get(),
            syncCursorStore = get(),
            serverClock = get(),
            timeProvider = get(),
            syncRecency = get()
        )
    }

    // The server's view of which timer is running. Passive: driven by user actions and the sync
    // manager, never by a loop of its own, so it needs no createdAtStart and no start() call.
    singleOf(::KtorRemoteActiveTimerDataSource) bind RemoteActiveTimerDataSource::class

    single {
        OfflineFirstActiveTimerRepository(
            remoteActiveTimerDataSource = get(),
            localProjectDataSource = get(),
            deviceIdProvider = get(),
            pendingSyncDataSource = get(),
            deltaSyncApplier = get(),
            syncScheduler = get(),
            serverClock = get(),
            timeProvider = get(),
            applicationScope = get(qualifier = named("AppScope"))
        )
    } bind ActiveTimerRepository::class

    // Only the timer reads this; everything else keeps using TimeProvider directly.
    singleOf(::DataStoreServerClockOffsetStore) bind ServerClockOffsetStore::class
    singleOf(::ServerClock)

    // How recently this device heard from the server; the timer freezes a foreign one without it.
    single { SyncRecency() }

    // Auth
    singleOf(::DataStoreSessionStorage) bind SessionStorage::class
    single { HttpClientFactory(get()).create(get()) }
    singleOf(::KtorAuthService) bind AuthService::class
    single { SocialAuthProvider() }
}
