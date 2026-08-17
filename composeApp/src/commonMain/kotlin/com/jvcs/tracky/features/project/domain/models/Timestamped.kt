package com.jvcs.tracky.features.project.domain.models

import kotlin.time.Instant

interface Timestamped {
    val ownUpdatedAt: Instant?
    val children: List<Timestamped> get() = emptyList()
    val lastUpdatedAt: Instant?
        get() = (children.mapNotNull { it.lastUpdatedAt } +
                listOfNotNull(ownUpdatedAt)).maxOrNull()
}