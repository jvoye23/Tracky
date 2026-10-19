package com.jvcs.tracky.analytics.api

interface AnalyticsLogger {

    fun log(event: AnalyticsEvent)

    fun setUserProperty(property: UserProperty)

    fun setUserId(id: String?)
}
