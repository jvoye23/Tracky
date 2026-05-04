package com.jvcs.tracky.core.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.jvcs.tracky.core.database.entity.ProjectSessionEntity
import com.jvcs.tracky.core.database.entity.SessionIntervalEntity

data class SessionWithIntervals(
    @Embedded val session: ProjectSessionEntity,
    @Relation(
        parentColumn = "recordId",
        entityColumn = "parentSessionId"
    )
    val intervals: List<SessionIntervalEntity>
)
