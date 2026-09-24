package com.jvcs.tracky.features.project.data.project

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jvcs.tracky.core.database.inMemoryTrackyDatabase
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.Project
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Instant

/** The project rows and their organisation, against a real in-memory database. */
internal class RoomLocalProjectDataSourceTest {

    private val db = inMemoryTrackyDatabase()
    private val projects = RoomLocalProjectDataSource(db.projectDao, db.projectTreeDao)
    private val organization = RoomLocalProjectOrganizationDataSource(db.projectDao, db.projectTreeDao, db.sortOrderDao)

    @AfterTest
    fun tearDown() = db.close()

    private suspend fun store(vararg rows: Project) = rows.forEach { projects.upsertProject(it) }

    @Test
    fun aStoredProjectReadsBackThroughEveryAccessor() =
        runBlocking {
            store(project("p1"))

            assertThat(projects.getProjectById("p1").map { it?.title }).isEqualTo(Result.Success("p1"))
            assertThat(
                projects.getProjectWithTasksByProjectId("p1").map {
                    it?.projectTasks
                },
            ).isEqualTo(Result.Success(emptyList()))
            assertThat(projects.observeProjectById("p1").first()?.projectId).isEqualTo("p1")
            assertThat(projects.observeProjectWithTaskTreeById("p1").first()?.projectId).isEqualTo("p1")
            assertThat(projects.getProjects().first().map { it.projectId }).isEqualTo(listOf("p1"))
            assertThat(projects.getActiveProjects().first().map { it.projectId }).isEqualTo(listOf("p1"))
        }

    @Test
    fun deletingOneOrAllRemovesThem() =
        runBlocking {
            store(project("p1"), project("p2"))

            assertThat(projects.deleteProject("p1")).isEqualTo(Result.Success(Unit))
            assertThat(projects.getProjectById("p1").map { it }).isEqualTo(Result.Success(null))
            assertThat(projects.deleteAllProjects()).isEqualTo(Result.Success(Unit))
            assertThat(projects.getProjects().first()).isEqualTo(emptyList())
        }

    @Test
    fun archivedTrashedAndPinnedProjectsAreListedApart() =
        runBlocking {
            store(
                project("archived").copy(isArchived = true),
                project("trashed").copy(trashedAt = Instant.fromEpochMilliseconds(1_000)),
                project("pinned").copy(isPinned = true),
            )

            assertThat(organization.getArchivedProjects().first().map { it.projectId }).isEqualTo(listOf("archived"))
            assertThat(organization.getTrashedProjects().first().map { it.projectId }).isEqualTo(listOf("trashed"))
            assertThat(organization.getPinnedProjects().map { list -> list.map { it.projectId } })
                .isEqualTo(Result.Success(listOf("pinned")))
            assertThat(organization.getExpiredTrashedProjectIds(Instant.fromEpochMilliseconds(2_000)))
                .isEqualTo(Result.Success(listOf("trashed")))
        }

    @Test
    fun sortIndicesAreWrittenAndReadBack() =
        runBlocking {
            store(project("p1"), project("p2"))

            organization.updateSortIndices(mapOf("p1" to 1L, "p2" to 0L), Instant.fromEpochMilliseconds(5_000))

            assertThat(organization.getSortIndices()).isEqualTo(Result.Success(mapOf("p1" to 1L, "p2" to 0L)))
            assertThat(projects.getProjectById("p1").map { it?.ownUpdatedAt })
                .isEqualTo(Result.Success(Instant.fromEpochMilliseconds(5_000)))
        }

    @Test
    fun anUnknownProjectReadsAsNull() =
        runBlocking {
            assertThat((projects.getProjectById("missing") as Result.Success).data).isNull()
        }

    private fun project(id: String) =
        Project(
            projectId = id,
            title = id,
            description = null,
            colorArgb = null,
            totalDurationMillis = null,
            startDateTimeUtc = Instant.fromEpochMilliseconds(0),
            isFinished = false,
            endDateTimeUtc = null,
        )
}
