package com.jvcs.tracky.features.project.domain.project

import com.jvcs.tracky.features.project.domain.models.Project

/**
 * The manual ("Custom") order: projects that have been reordered sort by their persisted sortIndex.
 * Projects with no sortIndex yet (newly created, or before any reorder) sort FIRST, newest first, so
 * a new project lands at the top of the list. startDateTimeUtc also breaks ties among indexed
 * projects, keeping the order deterministic (the read query has no ORDER BY).
 *
 * Shared by the overview ViewModel (what the user sees) and the repository (which re-indexes a
 * section when a project is pinned or unpinned), so both agree on what "the current order" means.
 */
fun List<Project>.sortedByCustomOrder(): List<Project> = sortedWith(
    compareBy<Project, Long?>(nullsFirst<Long>()) { it.sortIndex }
        .thenByDescending { it.startDateTimeUtc }
)
