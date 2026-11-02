package com.jvcs.tracky.core.data.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// The lint check wants onNewToken, but pushes only come from the Firebase console, so nothing needs
// the device's ID.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class TrackyFirebaseMessagingService : FirebaseMessagingService() {

    /** Only called while the app is open: FCM shows a background push without asking. */
    override fun onMessageReceived(message: RemoteMessage) {
        val push = message.notification ?: return
        val notification = PushNotificationFactory(this).build(push.title, push.body)
        getSystemService(NotificationManager::class.java).notify(message.messageId.hashCode(), notification)
    }
}
