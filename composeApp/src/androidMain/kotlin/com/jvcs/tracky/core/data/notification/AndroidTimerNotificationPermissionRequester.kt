package com.jvcs.tracky.core.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermission
import com.jvcs.tracky.core.domain.notification.TimerNotificationPermissionRequester
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.Permission
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.RequestCanceledException
import dev.icerock.moko.permissions.notifications.REMOTE_NOTIFICATION

/**
 * POST_NOTIFICATIONS, by way of moko-permissions.
 *
 * moko is here for one thing the hand-rolled version could not do: tell a refusal we may retry apart
 * from one we may not. It is bound to the Activity from [ComponentActivity.onCreate] rather than
 * through the library's `BindEffect`, because the notification coordinator starts at
 * `Application.onCreate` - long before anything composes - and because `BindEffect` would mean a
 * `@Composable expect fun`, which is the shape this refactor exists to delete.
 *
 * The moko controller stays private, so `:androidApp` can call [bind] without moko on its classpath.
 *
 * Application-scoped, so the refusal it remembers outlives the Activity the user leaves behind on
 * the way to the system settings page.
 */
class AndroidTimerNotificationPermissionRequester(
    private val applicationContext: Context,
    private val notificationController: AndroidTimerNotificationController,
) : TimerNotificationPermissionRequester {

    private val controller = PermissionsController(applicationContext)

    /**
     * Set while the last answer was a refusal, and the only reason [onReturnToApp] does anything.
     * Without it every resume would re-post a notification that is already showing correctly.
     */
    private var wasRefused = false

    /**
     * Hands the controller the Activity it launches from, and watches that Activity for the user
     * coming back from the system settings page.
     *
     * The moko binding unregisters itself on ON_DESTROY, and the observer is discarded with the
     * Activity's own lifecycle, so a rotation rebinds rather than accumulating.
     */
    fun bind(activity: ComponentActivity) {
        controller.bind(activity)
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) = onReturnToApp()
            },
        )
    }

    /**
     * Picks up a grant made outside the app.
     *
     * "Open App Settings" is a one-way trip: the user leaves, flips the toggle, and comes back to an
     * app that was never told. The ask does not repeat within a session, so without this the
     * notification they just enabled would stay hidden until the timer next changed state.
     */
    private fun onReturnToApp() {
        if (!wasRefused || !isGranted()) return
        wasRefused = false
        notificationController.repost()
    }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun request(): TimerNotificationPermission {
        // Checked first so an already-granted permission never round-trips through the launcher -
        // the same short-circuit the hand-rolled version made with checkSelfPermission. It reads off
        // the application context, so it also answers before any Activity exists.
        if (controller.isPermissionGranted(Permission.REMOTE_NOTIFICATION)) {
            return TimerNotificationPermission.Granted
        }

        return try {
            controller.providePermission(Permission.REMOTE_NOTIFICATION)
            // The service may already be up and invisible, having started before the grant arrived.
            // Android withholds a foreground-service notification posted without POST_NOTIFICATIONS
            // and does not reconsider once it is granted, so it has to be put up again.
            notificationController.repost()
            TimerNotificationPermission.Granted
        } catch (e: DeniedAlwaysException) {
            refused(TimerNotificationPermission.DeniedAlways)
        } catch (e: DeniedException) {
            refused(TimerNotificationPermission.Denied)
        } catch (e: RequestCanceledException) {
            // Dismissed without an answer. Counted as a decline: the timer still runs, it just has
            // nowhere to show itself, which is what the dialog says either way.
            refused(TimerNotificationPermission.Denied)
        }
    }

    /** Records the refusal so a later grant from the settings page can be noticed. */
    private fun refused(answer: TimerNotificationPermission): TimerNotificationPermission {
        wasRefused = true
        return answer
    }

    override fun openAppSettings() = controller.openAppSettings()
}
