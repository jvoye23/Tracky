package com.jvcs.tracky.analytics.impl

import com.jvcs.tracky.analytics.api.AnalyticsEvent
import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.api.UserProperty

class FirebaseAnalyticsLogger(private val backend: AnalyticsBackend) : AnalyticsLogger {

    override fun log(event: AnalyticsEvent) {
        val mapped = event.toFirebaseEvent()
        backend.logEvent(mapped.name, mapped.params.toFirebaseParams())
    }

    override fun setUserProperty(property: UserProperty) {
        backend.setUserProperty(property.name, property.value)
    }

    override fun setUserId(id: String?) {
        backend.setUserId(id)
    }
}

// Firebase takes String, Long and Double on both platforms; other types are dropped on Android.
private fun Map<String, Any>.toFirebaseParams(): Map<String, Any> =
    mapValues { (key, value) ->
        when (value) {
            is String, is Long, is Double -> value
            is Int -> value.toLong()
            is Float -> value.toDouble()
            is Boolean -> value.toString()
            else -> error("Unsupported analytics param type for '$key': ${value::class.simpleName}")
        }
    }
