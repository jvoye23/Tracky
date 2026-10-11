package com.jvcs.tracky.features.onboarding.data.di

import com.jvcs.tracky.features.onboarding.data.DataStoreOnboardingStatusStore
import com.jvcs.tracky.features.onboarding.domain.OnboardingStatusStore
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val onboardingDataModule =
    module {
        singleOf(::DataStoreOnboardingStatusStore) bind OnboardingStatusStore::class
    }
