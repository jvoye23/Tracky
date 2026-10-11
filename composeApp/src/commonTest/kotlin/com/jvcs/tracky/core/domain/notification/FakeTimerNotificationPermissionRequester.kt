package com.jvcs.tracky.core.domain.notification

import kotlinx.coroutines.CompletableDeferred

/** Answers [answer]; set [pending] to hold the answer back until the test completes it. */
class FakeTimerNotificationPermissionRequester(
    var answer: TimerNotificationPermission = TimerNotificationPermission.Granted,
    var needsPrompt: Boolean = true,
) : TimerNotificationPermissionRequester {

    var pending: CompletableDeferred<Unit>? = null

    var requests = 0
        private set

    override fun needsPrompt(): Boolean = needsPrompt

    override suspend fun request(): TimerNotificationPermission {
        requests++
        pending?.await()
        return answer
    }

    override fun openAppSettings() = Unit
}
