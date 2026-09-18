package com.jvcs.tracky.features.project.domain.task

import com.jvcs.tracky.features.project.domain.models.ProjectSubTask
import com.jvcs.tracky.features.project.domain.models.ProjectTask

/**
 * The order tasks and subtasks are shown in: the manual order the user dragged them into, with
 * anything never dragged falling back to creation order.
 *
 * Nulls sort **last** here, the opposite of [com.jvcs.tracky.features.project.domain.project.sortedByCustomOrder].
 * That is deliberate: a new project belongs at the top of the overview, but tasks are numbered
 * `01, 02, 03…` top-down and subtasks `1.1, 1.2…`, so a newly created one belongs at the bottom.
 * Keeping new rows at NULL is also what makes the sortIndex migration backfill-free.
 *
 * These are applied in the data mapper rather than in a ViewModel, because Room's `@Relation`
 * cannot carry an ORDER BY: without them the order of a task tree is whatever SQLite happens to
 * return, which a sync pull that rewrites rows can visibly shuffle.
 */
fun List<ProjectTask>.sortedByTaskOrder(): List<ProjectTask> = sortedWith(
    compareBy<ProjectTask, Long?>(nullsLast<Long>()) { it.sortIndex }
        .thenBy { it.startDateTimeUtc }
)

fun List<ProjectSubTask>.sortedBySubTaskOrder(): List<ProjectSubTask> = sortedWith(
    compareBy<ProjectSubTask, Long?>(nullsLast<Long>()) { it.sortIndex }
        .thenBy { it.startDateTimeUtc }
)
