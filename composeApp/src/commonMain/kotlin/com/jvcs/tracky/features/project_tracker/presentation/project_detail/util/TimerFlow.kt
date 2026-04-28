package com.jvcs.tracky.features.project_tracker.presentation.project_detail.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun timeAndEmit(emissionsPerSecond: Float): Flow<Duration> {
    return flow {
        var lastEmitTime = Clock.System.now().toEpochMilliseconds()
        emit(Duration.ZERO)

        while (true) {
            delay((1000L / emissionsPerSecond).roundToLong())

            val currentTime = Clock.System.now().toEpochMilliseconds()
            val elapsedTime = currentTime - lastEmitTime

            emit(elapsedTime.milliseconds)
            lastEmitTime = currentTime
        }
    }
}