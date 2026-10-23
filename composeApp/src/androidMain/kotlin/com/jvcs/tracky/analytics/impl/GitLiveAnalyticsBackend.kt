package com.jvcs.tracky.analytics.impl

import dev.gitlive.firebase.analytics.FirebaseAnalytics

class GitLiveAnalyticsBackend(private val firebase: FirebaseAnalytics) : AnalyticsBackend {

    override fun logEvent(name: String, params: Map<String, Any>) {
        firebase.logEvent(name, params)
    }

    override fun setUserProperty(name: String, value: String) {
        firebase.setUserProperty(name, value)
    }

    override fun setUserId(id: String?) {
        firebase.setUserId(id)
    }
}
