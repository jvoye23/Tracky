package com.jvcs.tracky.analytics.api

/** Records everything it is given, in order. */
class FakeAnalyticsLogger : AnalyticsLogger {

    private val loggedEvents = mutableListOf<AnalyticsEvent>()
    val events: List<AnalyticsEvent> get() = loggedEvents

    private val setUserProperties = mutableListOf<UserProperty>()
    val userProperties: List<UserProperty> get() = setUserProperties

    var userId: String? = null
        private set

    override fun log(event: AnalyticsEvent) {
        loggedEvents += event
    }

    override fun setUserProperty(property: UserProperty) {
        setUserProperties += property
    }

    override fun setUserId(id: String?) {
        userId = id
    }
}
