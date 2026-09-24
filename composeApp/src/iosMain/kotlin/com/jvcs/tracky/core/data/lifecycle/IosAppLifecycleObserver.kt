package com.jvcs.tracky.core.data.lifecycle

import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification

class IosAppLifecycleObserver : AppLifecycleObserver {

    override val isInForeground: Flow<Boolean> =
        callbackFlow {
            val center = NSNotificationCenter.defaultCenter

            val foregroundObserver =
                center.addObserverForName(
                    name = UIApplicationDidBecomeActiveNotification,
                    `object` = null,
                    queue = null,
                ) { _ -> trySend(true) }

            val backgroundObserver =
                center.addObserverForName(
                    name = UIApplicationDidEnterBackgroundNotification,
                    `object` = null,
                    queue = null,
                ) { _ -> trySend(false) }

            // Assume foreground on subscription (the manager only acts while online anyway).
            trySend(true)

            awaitClose {
                center.removeObserver(foregroundObserver)
                center.removeObserver(backgroundObserver)
            }
        }.distinctUntilChanged()
}
