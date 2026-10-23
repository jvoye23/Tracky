package com.jvcs.tracky.features.project.domain.subtask

import com.jvcs.tracky.analytics.api.AnalyticsLogger
import com.jvcs.tracky.analytics.api.UserProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the `sub_tasks_count` analytics user property on the number of stored subtasks, under any
 * project, and sets it only when its bucket changes.
 */
class SubTasksCountPropertySync(
    private val localSubTaskDataSource: LocalSubTaskDataSource,
    private val analyticsLogger: AnalyticsLogger,
    private val applicationScope: CoroutineScope,
) {

    private var started = false

    fun start() {
        if (started) return
        started = true

        applicationScope.launch {
            localSubTaskDataSource
                .observeSubTaskCount()
                .map { UserProperty.SubTasksCount(it) }
                // SubTasksCount is a data class over the raw count; two counts in one bucket must
                // not set the property twice, so compare what Firebase would receive.
                .distinctUntilChanged { old, new -> old.value == new.value }
                .collect { analyticsLogger.setUserProperty(it) }
        }
    }
}
