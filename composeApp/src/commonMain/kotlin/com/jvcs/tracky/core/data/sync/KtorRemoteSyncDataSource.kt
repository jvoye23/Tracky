package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.data.networking.dto.ProjectDto
import com.jvcs.tracky.core.data.networking.dto.SyncChangesDto
import com.jvcs.tracky.core.data.networking.dto.SyncImportResponseDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toImportProjectDto
import com.jvcs.tracky.core.data.networking.mappers.toSyncChanges
import com.jvcs.tracky.core.data.networking.post
import com.jvcs.tracky.core.domain.sync.RemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.EmptyResult
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.asEmptyDataResult
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.Project
import io.ktor.client.HttpClient

class KtorRemoteSyncDataSource(private val httpClient: HttpClient) : RemoteSyncDataSource {

    override suspend fun getChanges(since: Long?): Result<SyncChanges, DataError.Remote> =
        httpClient
            .get<SyncChangesDto>(
                route = CHANGES_ROUTE,
                // Omitted rather than sent as 0 when this device has never pulled: the server reads a
                // missing `since` as "everything, and no tombstones", which is what a fresh install
                // wants. Sending 0 means the same thing today, but only by coincidence.
                queryParams = since?.let { mapOf("since" to it) }.orEmpty(),
            ).map { it.toSyncChanges() }

    // The response's cursor is read but not kept: when the account already had data, a cursor from
    // here would skip everything this device has never pulled. See LocalDataImporter.
    override suspend fun importProjects(projects: List<Project>): EmptyResult<DataError.Remote> =
        httpClient
            .post<List<ProjectDto>, SyncImportResponseDto>(
                route = IMPORT_ROUTE,
                body = projects.map { it.toImportProjectDto() },
            ).asEmptyDataResult()

    private companion object {
        const val CHANGES_ROUTE = "/api/sync/changes"
        const val IMPORT_ROUTE = "/api/sync/import"
    }
}
