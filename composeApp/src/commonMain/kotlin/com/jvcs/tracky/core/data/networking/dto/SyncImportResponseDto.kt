package com.jvcs.tracky.core.data.networking.dto

import kotlinx.serialization.Serializable

/** The response of `POST /api/sync/import`. */
@Serializable
data class SyncImportResponseDto(
    /**
     * The highest change sequence the import wrote - or, when every id was already there, the
     * caller's current maximum - so it is always a usable cursor.
     */
    val cursor: Long,
    val serverNowUtc: String? = null,
)
