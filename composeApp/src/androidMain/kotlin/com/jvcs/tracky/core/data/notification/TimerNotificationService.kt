package com.jvcs.tracky.core.data.notification

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.jvcs.tracky.core.domain.notification.TimerNotificationCoordinator
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.core.domain.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Keeps the running timer on screen while the app is not.
 *
 * START_NOT_STICKY is load-bearing. If the process dies the timer really is stranded, and
 * StrandedTimerReconciler parks its interval at the next launch; a sticky restart would put a
 * notification back up for a row that has just been parked, and offer Pause on it.
 *
 * Dependencies come from the global Koin context the same way SyncWorker takes them, so there is
 * no factory to register.
 */
class TimerNotificationService : Service(), KoinComponent {

    private val controller: AndroidTimerNotificationController by inject()
    private val coordinator: TimerNotificationCoordinator by inject()
    private val timeProvider: TimeProvider by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenOn = MutableStateFlow(true)
    private lateinit var factory: TimerNotificationFactory
    private var tickJob: Job? = null

    /** Nothing to redraw behind a dark screen, and every redraw is a wakeup. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenOn.value = intent?.action != Intent.ACTION_SCREEN_OFF
        }
    }

    override fun onCreate() {
        super.onCreate()
        factory = TimerNotificationFactory(this)
        factory.createChannel()

        screenOn.value = getSystemService(PowerManager::class.java).isInteractive
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        )

        scope.launch {
            controller.session.collectLatest { session ->
                if (session == null) takeDown() else show(session)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    private fun show(session: TimerNotificationSession) {
        tickJob?.cancel()
        startInForeground(session)
        if (!session.isRunning) return

        tickJob = scope.launch {
            // collectLatest cancels the loop outright when the screen goes off, rather than
            // leaving it spinning on a notify nobody can see.
            screenOn.collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    val elapsed = session.elapsedAt(timeProvider.nowInstant)
                    // Sleep to the next whole second of elapsed, not a flat second from an
                    // arbitrary moment: a fixed delay leaves the notification showing a second
                    // the in-app clock has already left behind, and the two must agree.
                    delay(TICK_MILLIS - elapsed.inWholeMilliseconds % TICK_MILLIS)
                    NotificationManagerCompat.from(this@TimerNotificationService).notify(
                        TimerNotificationFactory.NOTIFICATION_ID,
                        factory.build(session, session.elapsedAt(timeProvider.nowInstant))
                    )
                }
            }
        }
    }

    private fun takeDown() {
        tickJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PAUSE = "com.jvcs.tracky.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.jvcs.tracky.action.RESUME_TIMER"
        private const val TICK_MILLIS = 1_000L
    }
}
