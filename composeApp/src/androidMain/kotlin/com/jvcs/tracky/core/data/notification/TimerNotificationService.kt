package com.jvcs.tracky.core.data.notification

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.core.domain.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the running timer on screen while the app is not.
 *
 * The notification is only re-posted when the session changes. The clock between changes is a
 * Chronometer that SystemUI ticks on its own (see TimerNotificationFactory), so there is no
 * per-second loop here and nothing to stop when the screen goes dark.
 *
 * START_NOT_STICKY is load-bearing. If the process dies the timer really is stranded, and
 * StrandedTimerReconciler parks its interval at the next launch; a sticky restart would put a
 * notification back up for a row that has just been parked, and offer Pause on it.
 *
 * Dependencies come from the global Koin context the same way SyncWorker takes them, so there is
 * no factory to register.
 */
class TimerNotificationService :
    Service(),
    KoinComponent {

    private val controller: AndroidTimerNotificationController by inject()
    private val coordinator: TimerNotificationCoordinator by inject()
    private val timeProvider: TimeProvider by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var factory: TimerNotificationFactory

    override fun onCreate() {
        super.onCreate()
        factory = TimerNotificationFactory(this)
        factory.createChannel()

        scope.launch {
            controller.session.collectLatest { session ->
                if (session == null) takeDown() else startInForeground(session)
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> scope.launch { coordinator.onPause() }
            ACTION_RESUME -> scope.launch { coordinator.onResume() }
        }

        // The controller always sets the session before starting us, so there is something to show
        // inside the five seconds startForegroundService allows. A null here means the timer was
        // stopped in between, and the right answer is to go away again.
        val session = controller.session.value
        if (session == null) {
            takeDown()
            return START_NOT_STICKY
        }
        startInForeground(session)
        return START_NOT_STICKY
    }

    private fun startInForeground(session: TimerNotificationSession) {
        ServiceCompat.startForeground(
            this,
            TimerNotificationFactory.NOTIFICATION_ID,
            factory.build(session, session.elapsedAt(timeProvider.nowInstant)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun takeDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE = "com.jvcs.tracky.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.jvcs.tracky.action.RESUME_TIMER"
    }
}
