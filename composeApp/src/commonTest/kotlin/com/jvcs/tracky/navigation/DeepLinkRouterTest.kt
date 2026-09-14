package com.jvcs.tracky.navigation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkRouterTest {

    private val router = DeepLinkRouter()
    private fun detail(id: String) = Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = id)

    @Test
    fun aRequestMadeBeforeAnythingIsListeningIsStillDelivered() = runTest {
        // The nav host is not composed while the session read is still in flight, so a tap on the
        // notification from a cold start always beats its own collector.
        router.request(detail("p1"))

        assertEquals(detail("p1"), router.requests.first())
    }

    @Test
    fun theNewestRequestWins() = runTest {
        router.request(detail("p1"))
        router.request(detail("p2"))

        assertEquals(detail("p2"), router.requests.first())
    }

    @Test
    fun aConsumedRequestIsNotReplayedToTheNextCollector() = runTest {
        router.request(detail("p1"))
        router.requests.first()
        router.consume()

        // Otherwise a rotation would navigate the user back out of wherever they had gone.
        assertNull(withTimeoutOrNull(50) { router.requests.firstOrNull() })
    }
}
