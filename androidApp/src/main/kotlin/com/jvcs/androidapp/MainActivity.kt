package com.jvcs.androidapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jvcs.tracky.App
import com.jvcs.tracky.core.data.notification.AndroidTimerNotificationPermissionRequester
import com.jvcs.tracky.core.data.notification.TimerNotificationIntents
import com.jvcs.tracky.navigation.DeepLinkRouter
import com.jvcs.tracky.navigation.Route
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val deepLinkRouter: DeepLinkRouter by inject()

    // Bound here rather than through moko's BindEffect: the notification coordinator starts at
    // Application.onCreate, long before anything composes, so binding in composition would be too
    // late for a timer that is already running when the app opens.
    private val timerNotificationPermissionRequester:
        AndroidTimerNotificationPermissionRequester by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        var shouldShowSplashScreen = true

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                shouldShowSplashScreen
            }
        }
        super.onCreate(savedInstanceState)
        timerNotificationPermissionRequester.bind(this)
        enableEdgeToEdge()
        routeDeepLink(intent)
        setContent {
            App(
                onAuthenticationChecked = {
                    shouldShowSplashScreen = false
                },
            )
        }
    }

    // launchMode is singleTop, so a tap while Tracky is already open lands here rather than
    // rebuilding the activity.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeDeepLink(intent)
    }

    private fun routeDeepLink(intent: Intent?) {
        val projectId =
            intent
                ?.getStringExtra(TimerNotificationIntents.EXTRA_PROJECT_ID)
                ?: return
        // Taken off as it is read. The activity keeps this intent for its whole life and there is
        // no configChanges, so a rotation runs onCreate against it again - without this the deep
        // link fires a second time and throws the user back out of wherever they had got to.
        intent.removeExtra(TimerNotificationIntents.EXTRA_PROJECT_ID)
        deepLinkRouter.request(
            Route.ProjectRoute.ProjectDetail(isEditMode = false, projectId = projectId),
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
