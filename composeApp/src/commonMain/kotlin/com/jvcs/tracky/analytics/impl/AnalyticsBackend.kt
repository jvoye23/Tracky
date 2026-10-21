package com.jvcs.tracky.analytics.impl

/**
 * The platform's Firebase Analytics SDK: GitLive on Android, the Swift SDK on iOS. iOS keeps Firebase
 * out of Kotlin/Native, whose test executable links without Xcode and so cannot find the frameworks.
 */
interface AnalyticsBackend {

    fun logEvent(name: String, params: Map<String, Any>)

    fun setUserProperty(name: String, value: String)

    fun setUserId(id: String?)
}
