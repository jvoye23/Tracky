package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.notification.TimerNotificationSession
import com.jvcs.tracky.features.project.domain.timer.ProjectRef
import com.jvcs.tracky.features.project.domain.timer.TaskRef
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** The ongoing timer notification, as the system and every fallback renderer read it. */
class TimerNotificationFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = TimerNotificationFactory(context)

    private fun session(
        isRunning: Boolean,
        subTask: TaskRef? = null,
        colorArgb: Int? = 0xFF336699.toInt(),
    ) = TimerNotificationSession(
        project = ProjectRef(id = "p1", title = "Client work", colorArgb = colorArgb),
        useLightTextColor = true,
        task = TaskRef(id = "t1", title = "Design review"),
        subTask = subTask,
        elapsed = 12.minutes,
        asOf = Instant.fromEpochMilliseconds(0),
        isRunning = isRunning,
    )

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    private val Notification.isOngoing get() = flags and Notification.FLAG_ONGOING_EVENT != 0

    @Test
    fun theChannelIsCreatedQuiet() {
        factory.createChannel()

        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(TimerNotificationFactory.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun aRunningTaskTimerIsOngoingAndNamesTaskAndProject() {
        val notification = factory.build(session(isRunning = true), 12.minutes)

        assertThat(notification.title).isEqualTo("Design review")
        assertThat(notification.text).isEqualTo("Client work")
        assertThat(notification.isOngoing).isTrue()
        assertThat(notification.channelId).isEqualTo(TimerNotificationFactory.CHANNEL_ID)
        assertThat(notification.category).isEqualTo(Notification.CATEGORY_STOPWATCH)
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PUBLIC)
        assertThat(notification.color).isEqualTo(0xFF336699.toInt())
    }

    @Test
    fun aSubTaskTimerNamesTheSubTaskUnderItsTask() {
        val notification = factory.build(session(isRunning = true, subTask = TaskRef("s1", "Colours")), 3.minutes)

        assertThat(notification.title).isEqualTo("Design review")
        assertThat(notification.text).isEqualTo("Colours")
    }

    @Test
    fun aPausedTimerCanBeDismissed() {
        val notification = factory.build(session(isRunning = false, colorArgb = null), 12.minutes)

        assertThat(notification.isOngoing).isFalse()
        assertThat(notification.contentView).isNotNull()
        assertThat(notification.bigContentView).isNotNull()
    }
}
