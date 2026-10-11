package com.jvcs.tracky.core.domain.sync

import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.Project

/** Records every import; [importResults] answers them in order, then [importResult] for the rest. */
class FakeRemoteSyncDataSource : RemoteSyncDataSource {

    var importResult: EmptyResult<DataError.Remote> = Result.Success(Unit)
    val importResults = ArrayDeque<EmptyResult<DataError.Remote>>()

    /** Called after each import, e.g. to simulate a write landing while the request is in flight. */
    var onImport: suspend () -> Unit = {}

    val importedProjectIds = mutableListOf<String>()

    /** Every import request, failed ones included. */
    var importAttempts = 0
        private set

    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> =
        Result.Error(DataError.Remote.NOT_FOUND)

    override suspend fun importProjects(projects: List<Project>): EmptyResult<DataError.Remote> {
        importAttempts++
        val result = importResults.removeFirstOrNull() ?: importResult
        if (result is Result.Success) importedProjectIds += projects.map { it.projectId }
        onImport()
        return result
    }
}
