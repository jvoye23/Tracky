package com.jvcs.tracky.core.data.sync

import com.jvcs.tracky.core.data.networking.dto.SyncChangesDto
import com.jvcs.tracky.core.data.networking.get
import com.jvcs.tracky.core.data.networking.mappers.toSyncChanges
import com.jvcs.tracky.core.domain.sync.RemoteSyncDataSource
import com.jvcs.tracky.core.domain.sync.SyncChanges
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
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

    private companion object {
        const val CHANGES_ROUTE = "/api/sync/changes"
    }
}
