package com.jvcs.tracky.features.onboarding.presentation.di

import com.jvcs.tracky.features.onboarding.presentation.paywall.PaywallViewModel
import com.jvcs.tracky.features.onboarding.presentation.permission.NotificationPermissionViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val onboardingPresentationModule =
    module {
        viewModelOf(::NotificationPermissionViewModel)
        viewModelOf(::PaywallViewModel)
    }
