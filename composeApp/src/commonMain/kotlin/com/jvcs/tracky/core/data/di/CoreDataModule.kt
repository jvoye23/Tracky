package com.jvcs.tracky.core.data.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.jvcs.tracky.core.data.auth.DataStoreSessionStorage
import com.jvcs.tracky.core.data.auth.KtorAuthService
import com.jvcs.tracky.core.data.networking.HttpClientFactory
import com.jvcs.tracky.core.database.DatabaseFactory
import com.jvcs.tracky.core.database.TrackyDatabase
import com.jvcs.tracky.core.domain.auth.AuthService
import com.jvcs.tracky.core.domain.auth.SessionStorage
import com.jvcs.tracky.core.domain.auth.SocialAuthProvider
import com.jvcs.tracky.features.project_tracker.data.RoomLocalProjectDataSource
import com.jvcs.tracky.features.project_tracker.domain.ProjectRepository
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

expect val platformCoreDataModule: Module

val coreDataModule = module {
    includes(platformCoreDataModule)

    single { get<TrackyDatabase>().projectDao }

    singleOf(::RoomLocalProjectDataSource) bind ProjectRepository::class

    single {
        Json {
            ignoreUnknownKeys = true
        }
    }
    single {
        get<DatabaseFactory>()
            .create()
            .addMigrations(TrackyDatabase.MIGRATION_1_2, TrackyDatabase.MIGRATION_2_3, TrackyDatabase.MIGRATION_3_4)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    // Auth
    singleOf(::DataStoreSessionStorage) bind SessionStorage::class
    single { HttpClientFactory(get()).create(get()) }
    singleOf(::KtorAuthService) bind AuthService::class
    single { SocialAuthProvider() }
}
