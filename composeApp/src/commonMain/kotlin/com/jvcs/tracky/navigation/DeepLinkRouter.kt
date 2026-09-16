package com.jvcs.tracky.navigation

/**
 * Where a destination arriving from outside the app - a notification tap, later a Live Activity -
 * is handed to the navigator.
 *
 * Navigation 3 has no deep-link matcher: the back stack is a list the app owns, so linking in means
 * translating the incoming request into a [Route] and seeding that list. This is the seam between
 * the two halves, and it lives in commonMain so iOS uses the same one.
 *
 * A request made while nothing is listening is held, because the nav host is not composed while the
 * session read is still in flight and a cold-start tap would otherwise be shouted into an empty
 * room. Delivery clears it again, so the same request cannot navigate twice - after a rotation, say
 * - and there is no separate consume step for the caller to forget.
 *
 * Main thread only. Both callers deliver on it, and the listener seeds a back stack that Compose
 * reads there.
 */
class DeepLinkRouter {

    private var pending: Route? = null

    var listener: ((Route) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                pending?.let { route ->
                    // Cleared before the call, so a listener that routes back in cannot be handed
                    // the same request a second time.
                    pending = null
                    value.invoke(route)
                }
            }
        }

    fun request(route: Route) {
        val target = listener
        if (target == null) pending = route else target.invoke(route)
    }
}
