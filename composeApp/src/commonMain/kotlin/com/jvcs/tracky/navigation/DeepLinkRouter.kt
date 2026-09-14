package com.jvcs.tracky.navigation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where a destination arriving from outside the app - a notification tap, later a Live Activity -
 * is handed to the navigator.
 *
 * Navigation 3 has no deep-link matcher: the back stack is a list the app owns, so linking in means
 * translating the incoming intent into a [Route] and seeding that list. This is the seam between
 * the two halves, and it lives in commonMain so iOS uses the same one.
 *
 * It replays, because the nav host is not composed while the session read is still in flight and a
 * cold-start tap would otherwise be emitted into an empty room. [consume] is what stops the replayed
 * request from navigating again on the next subscription, such as after a rotation.
 */
class DeepLinkRouter {

    private val _requests = MutableSharedFlow<Route>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val requests: Flow<Route> = _requests.asSharedFlow()

    fun request(route: Route) {
        _requests.tryEmit(route)
    }

    fun consume() {
        _requests.resetReplayCache()
    }
}
