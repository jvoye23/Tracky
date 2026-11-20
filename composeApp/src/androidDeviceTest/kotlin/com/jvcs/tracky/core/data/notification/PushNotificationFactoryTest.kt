package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.jvcs.tracky.composeapp.R
import kotlin.test.Test

/** The channel console pushes land on, and the notification for one that arrives in the foreground. */
class PushNotificationFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val factory = PushNotificationFactory(context)
    private val channelId = context.getString(R.string.push_notification_channel_id)

    private val Notification.title get() = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    private val Notification.text get() = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

    @Test
    fun theChannelIsCreatedWithDefaultImportance() {
        factory.createChannel()

        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)
        assertThat(channel).isNotNull()
        assertThat(channel.name.toString()).isEqualTo("Announcements")
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_DEFAULT)
    }

    @Test
    fun aPushShowsItsTextOnThePushChannel() {
        val notification = factory.build(title = "New in Tracky", body = "Pushes have landed")

        assertThat(notification.title).isEqualTo("New in Tracky")
        assertThat(notification.text).isEqualTo("Pushes have landed")
        assertThat(notification.channelId).isEqualTo(channelId)
    }

    /** The content intent is not asserted: this library's test APK has no launcher activity to open. */
    @Test
    fun tappingAPushDismissesIt() {
        val notification = factory.build(title = "New in Tracky", body = "Pushes have landed")

        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isEqualTo(Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun aPushWithoutTextStillBuilds() {
        val notification = factory.build(title = null, body = null)

        assertThat(notification.title).isNull()
        assertThat(notification.text).isNull()
    }
}
