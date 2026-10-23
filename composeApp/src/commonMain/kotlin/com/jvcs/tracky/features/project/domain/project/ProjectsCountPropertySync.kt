package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.api.UserProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the `projects_count` analytics user property on the number of stored projects. Counted like
 * the free-project limit, archived and trashed projects included, and set only when its bucket
 * changes.
 */
class ProjectsCountPropertySync(
    private val localProjectDataSource: LocalProjectDataSource,
    private val analyticsLogger: AnalyticsLogger,
    private val applicationScope: CoroutineScope,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            localProjectDataSource
                .observeProjectCount()
                .map { UserProperty.ProjectsCount(it) }
                // ProjectsCount is a data class over the raw count; two counts in one bucket must
                // not set the property twice, so compare what Firebase would receive.
                .distinctUntilChanged { old, new -> old.value == new.value }
                .collect { analyticsLogger.setUserProperty(it) }
        }
    }
}
