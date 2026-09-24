package com.jvcs.tracky.features.project.data.mappers

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.core.database.entity.ProjectEntity
import com.jvcs.tracky.core.database.entity.ProjectTaskEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTaskTreeEntity
import com.jvcs.tracky.core.database.relation.ProjectWithTasksEntity
import com.jvcs.tracky.core.database.relation.TaskWithSubTasks
import com.jvcs.tracky.features.project.domain.models.Project
import kotlin.test.Test
import kotlin.time.Instant

internal class ProjectMapperTest {

    @Test
    fun aProjectSurvivesTheRoundTripThroughItsEntity() {
        assertThat(project.toProjectEntity().toProject()).isEqualTo(project)
    }

    @Test
    fun anUnsetProjectKeepsItsNullsThroughTheEntity() {
        val bare = project.copy(endDateTimeUtc = null, trashedAt = null, ownUpdatedAt = null, sortIndex = null)

        val entity = bare.toProjectEntity()

        assertThat(entity.endDateTimeEpochMs).isNull()
        assertThat(entity.trashedAtEpochMs).isNull()
        assertThat(entity.toProject()).isEqualTo(bare)
    }

    @Test
    fun bothRelationsCarryTheProjectAndOrderItsTasks() {
        val entity = project.toProjectEntity()
        val tasks = listOf(taskEntity("late", sortIndex = 2), taskEntity("early", sortIndex = 1))

        val flat = ProjectWithTasksEntity(entity, tasks).toProject()
        val tree =
            ProjectWithTaskTreeEntity(
                entity,
                tasks.map { TaskWithSubTasks(it, emptyList(), emptyList()) },
            ).toProject()

        listOf(flat, tree).forEach { mapped ->
            assertThat(mapped.copy(projectTasks = null)).isEqualTo(project)
            assertThat(mapped.projectTasks.orEmpty().map { it.projectTaskId }).containsExactly("early", "late")
        }
    }

    @Test
    fun theCreateRequestFillsTheFieldsTheServerRequires() {
        val request = project.copy(description = null, colorArgb = null).toCreateProjectRequest()

        assertThat(request.id).isEqualTo("p1")
        assertThat(request.description).isEqualTo("")
        assertThat(request.color).isEqualTo(0)
        assertThat(request.startDateTimeUtc).isEqualTo("2026-08-01T09:00:00Z")
        assertThat(request.updatedAtUtc).isEqualTo("2026-08-03T09:00:00Z")
        assertThat(request.sortIndex).isEqualTo(4L)
    }

    @Test
    fun theUpdateRequestCarriesEveryEditableField() {
        val request = project.toUpdateProjectRequest()

        assertThat(request.title).isEqualTo("Garden")
        assertThat(request.color).isEqualTo(0xFF00FF00.toInt())
        assertThat(request.totalDuration).isEqualTo(60_000L)
        assertThat(request.endDateTimeUtc).isEqualTo("2026-08-02T09:00:00Z")
        assertThat(request.trashedAtUtc).isEqualTo("2026-08-04T09:00:00Z")
        assertThat(request.isPinned).isEqualTo(true)
        assertThat(request.isArchived).isEqualTo(true)
        assertThat(request.isFinished).isEqualTo(true)
        assertThat(request.sortIndex).isEqualTo(4L)
    }

    private companion object {
        val project =
            Project(
                projectId = "p1",
                title = "Garden",
                description = "Spring work",
                colorArgb = 0xFF00FF00.toInt(),
                totalDurationMillis = 60_000L,
                startDateTimeUtc = Instant.parse("2026-08-01T09:00:00Z"),
                isFinished = true,
                useLightTextColor = true,
                endDateTimeUtc = Instant.parse("2026-08-02T09:00:00Z"),
                isArchived = true,
                trashedAt = Instant.parse("2026-08-04T09:00:00Z"),
                isPinned = true,
                ownUpdatedAt = Instant.parse("2026-08-03T09:00:00Z"),
                sortIndex = 4L,
            )

        fun taskEntity(id: String, sortIndex: Long) =
            ProjectTaskEntity(
                projectTaskId = id,
                parentProjectId = "p1",
                title = id,
                description = null,
                durationMillis = 0L,
                startDateTimeEpochMs = 0L,
                endDateTimeEpochMs = null,
                isFinished = false,
                isTimerRunning = false,
                sortIndex = sortIndex,
            )
    }
}
