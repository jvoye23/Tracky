package com.jvcs.tracky.features.project.domain.models

enum class ProjectStatus {
    ACTIVE,
    FINISHED,
    ARCHIVED,
    TRASHED
}

val Project.status: ProjectStatus
    get() = when {
        trashedAt != null -> ProjectStatus.TRASHED
        isFinished -> ProjectStatus.FINISHED
        isArchived -> ProjectStatus.ARCHIVED
        else -> ProjectStatus.ACTIVE
    }