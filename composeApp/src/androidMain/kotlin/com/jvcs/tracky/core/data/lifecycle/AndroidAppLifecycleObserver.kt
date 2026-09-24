package com.jvcs.tracky.core.data.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.jvcs.tracky.core.domain.lifecycle.AppLifecycleObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AndroidAppLifecycleObserver : AppLifecycleObserver {

    override val isInForeground: Flow<Boolean> =
        callbackFlow {
            val observer =
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        trySend(true)
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        trySend(false)
                    }
                }

            // ProcessLifecycleOwner must be observed on the main thread.
            val mainHandler = Handler(Looper.getMainLooper())
            mainHandler.post {
                ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
            }

            awaitClose {
                mainHandler.post {
                    ProcessLifecycleOwner.get().lifecycle.removeObserver(observer)
                }
            }
        }.distinctUntilChanged()
}
