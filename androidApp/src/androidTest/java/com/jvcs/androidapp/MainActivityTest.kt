package com.jvcs.androidapp

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.jvcs.tracky.core.data.notification.TimerNotificationIntents.EXTRA_PROJECT_ID
import com.jvcs.tracky.navigation.DeepLinkRouter
import com.jvcs.tracky.navigation.Route
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val router = GlobalContext.get().get<DeepLinkRouter>()
    private val routes = CopyOnWriteArrayList<Route>()

    // The request is made in onCreate, before anything composes, so a listener set first sees it
    // ahead of the nav host's own.
    private fun listen() {
        router.listener = { routes += it }
    }

    @After
    fun tearDown() {
        router.listener = null
    }

    private fun intent(projectId: String? = null) =
        Intent(context, MainActivity::class.java).apply {
            projectId?.let { putExtra(EXTRA_PROJECT_ID, it) }
        }

    @Test
    fun aPlainLaunchRequestsNothing() {
        listen()
        ActivityScenario.launch<MainActivity>(intent()).use {
            assertThat(routes).isEmpty()
        }
    }

    @Test
    fun aNotificationTapOpensItsProjectOnceAndIsConsumed() {
        listen()
        ActivityScenario.launch<MainActivity>(intent("project-1")).use { scenario ->
            scenario.onActivity { activity ->
                assertThat(activity.intent.hasExtra(EXTRA_PROJECT_ID)).isFalse()
            }
            // A rotation runs onCreate again against the same intent.
            scenario.recreate()
        }

        assertThat(routes).containsExactly(Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = "project-1"))
    }

    @Test
    fun aTapWhileTheAppIsOpenArrivesAsANewIntent() {
        ActivityScenario.launch<MainActivity>(intent()).use { scenario ->
            scenario.onActivity { listen() }

            context.startActivity(
                intent("project-2")
                    .putExtra(MARKER, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            // The system delivers it asynchronously, so wait for the route rather than one idle pass.
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (routes.isEmpty() && SystemClock.uptimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }

            scenario.onActivity { activity ->
                assertThat(activity.intent.getBooleanExtra(MARKER, false)).isTrue()
                assertThat(activity.intent.hasExtra(EXTRA_PROJECT_ID)).isFalse()
            }
        }

        assertThat(routes).containsExactly(Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = "project-2"))
    }

    private companion object {
        const val MARKER = "marker"
    }
}
