package com.jvcs.tracky.core.data.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.jvcs.tracky.composeapp.R

/**
 * The channel console pushes arrive on, and the notification for one that lands while the app is
 * open. In the background FCM draws it itself, from the defaults in the app's manifest.
 */
class PushNotificationFactory(private val context: Context) {

    private val channelId = context.getString(R.string.push_notification_channel_id)

    fun createChannel() {
        val channel =
            NotificationChannel(
                channelId,
                context.getString(R.string.push_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.push_notification_channel_description)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(title: String?, body: String?): Notification =
        NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_tracky_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent())
            .build()

    private fun openAppPendingIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
}
