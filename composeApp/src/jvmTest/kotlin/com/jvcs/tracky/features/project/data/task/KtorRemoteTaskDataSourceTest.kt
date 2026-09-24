package com.jvcs.tracky.features.project.data.task

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.SentRequest
import com.jvcs.tracky.core.data.networking.mockHttpClient
import com.jvcs.tracky.core.data.networking.respondJson
import com.jvcs.tracky.core.data.networking.respondNoContent
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.ProjectTask
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

internal class KtorRemoteTaskDataSourceTest {

    private val sent = mutableListOf<SentRequest>()

    private val dataSource =
        KtorRemoteTaskDataSource(
            mockHttpClient(sent) { request ->
                when (request.method) {
                    HttpMethod.Get -> respondJson("[$TASK_JSON]")
                    HttpMethod.Delete -> respondNoContent()
                    else -> if (request.url.encodedPath.endsWith("sort")) respondNoContent() else respondJson(TASK_JSON)
                }
            },
        )

    @Test
    fun readsAndWritesGoToTheProjectsTaskRoutes() =
        runTest {
            val titles =
                listOf(
                    dataSource.getTasksByProjectId("p1").map { list -> list.single().title },
                    dataSource.postTaskByProjectId("p1", task).map { it.title },
                    dataSource.updateTaskByProjectId("p1", task).map { it.title },
                )

            assertThat(titles).isEqualTo(List(3) { Result.Success("Dig beds") })
            assertThat(dataSource.deleteTask("p1", "t1")).isEqualTo(Result.Success(Unit))
            assertThat(sent.map { it.method to it.path }).isEqualTo(
                listOf(
                    HttpMethod.Get to "/api/projects/p1/tasks",
                    HttpMethod.Post to "/api/projects/p1/tasks",
                    HttpMethod.Put to "/api/projects/p1/tasks/t1",
                    HttpMethod.Delete to "/api/projects/p1/tasks/t1",
                ),
            )
        }

    @Test
    fun aReorderIsOneRequestCarryingEveryIndex() =
        runTest {
            val result =
                dataSource.reorderTasks(
                    "p1",
                    mapOf("t1" to 0L, "t2" to 1L),
                    Instant.parse("2026-09-01T10:00:00Z"),
                )

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(sent.single().path).isEqualTo("/api/projects/p1/tasks/sort")
            assertThat(sent.single().body).contains("\"id\":\"t2\"")
        }

    private companion object {
        const val TASK_JSON = """{"id":"t1","title":"Dig beds","startDateTimeUtc":"2026-08-01T09:00:00Z"}"""

        val task =
            ProjectTask(
                projectTaskId = "t1",
                title = "Dig beds",
                description = null,
                durationMillis = 0L,
                startDateTimeUtc = Instant.parse("2026-08-01T09:00:00Z"),
                parentProjectId = "p1",
                isTimerRunning = false,
            )
    }
}
