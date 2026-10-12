package com.jvcs.tracky.core.data.dto

import kotlinx.serialization.Serializable

/** The entitlement block of `GET /api/entitlement` and `POST /api/entitlement/refresh`. */
@Serializable
data class EntitlementDto(
    val entitlement: String,
    val entitlementExpiresAt: String? = null,
    val isGrandfathered: Boolean = false,
)
