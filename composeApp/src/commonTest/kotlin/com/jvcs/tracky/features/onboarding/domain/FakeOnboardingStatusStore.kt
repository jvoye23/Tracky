package com.jvcs.tracky.features.onboarding.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeOnboardingStatusStore(initial: OnboardingStatus = OnboardingStatus.NOT_STARTED) : OnboardingStatusStore {

    private val status = MutableStateFlow(initial)

    val current: OnboardingStatus get() = status.value

    override fun observe(): Flow<OnboardingStatus> = status

    override suspend fun set(status: OnboardingStatus) {
        this.status.value = status
    }
}
