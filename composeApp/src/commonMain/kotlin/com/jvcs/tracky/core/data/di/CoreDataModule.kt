package com.jvcs.tracky.core.data.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.data.KtorRemoteProjectDataSource
import com.jvcs.tracky.core.data.auth.DataStoreSessionStorage
import com.jvcs.tracky.core.data.auth.KtorAuthService
import com.jvcs.tracky.core.data.networking.HttpClientFactory
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.domain.RemoteProjectDataSource
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.features.project_tracker.data.OfflineFirstProjectRepository
import com.jvcs.tracky.features.project_tracker.data.RoomLocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.LocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformCoreDataModule: Module

val coreDataModule = module {
    includes(platformCoreDataModule)

    single { get<TrackyDatabase>().projectDao }
    single { get<TrackyDatabase>().pendingSyncDao }

    singleOf(::RoomLocalProjectDataSource) bind LocalProjectDataSource::class
    singleOf(::KtorRemoteProjectDataSource) bind RemoteProjectDataSource::class
    single<ProjectRepository> {
        OfflineFirstProjectRepository(
            localProjectDataSource = get(),
            remoteProjectDataSource = get(),
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
            )
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    // Auth
    singleOf(::DataStoreSessionStorage) bind SessionStorage::class
    single { HttpClientFactory(get()).create(get()) }
    singleOf(::KtorAuthService) bind AuthService::class
    single { SocialAuthProvider() }
}
