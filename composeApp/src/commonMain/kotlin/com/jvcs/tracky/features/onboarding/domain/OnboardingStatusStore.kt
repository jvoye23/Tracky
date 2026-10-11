package com.jvcs.tracky.features.onboarding.domain

import kotlinx.coroutines.flow.Flow

/** Persists [OnboardingStatus] across launches. */
interface OnboardingStatusStore {

    /** Emits the stored status first, then every change. */
    fun observe(): Flow<OnboardingStatus>

    suspend fun set(status: OnboardingStatus)
}
