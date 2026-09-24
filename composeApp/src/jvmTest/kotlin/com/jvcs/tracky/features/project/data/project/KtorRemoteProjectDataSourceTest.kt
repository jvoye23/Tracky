package com.jvcs.tracky.features.project.data.project

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.jvcs.tracky.core.data.networking.SentRequest
import com.jvcs.tracky.core.data.networking.mockHttpClient
import com.jvcs.tracky.core.data.networking.respondJson
import com.jvcs.tracky.core.data.networking.respondNoContent
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.core.domain.util.map
import com.jvcs.tracky.features.project.domain.models.Project
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

internal class KtorRemoteProjectDataSourceTest {

    private val sent = mutableListOf<SentRequest>()

    private val dataSource =
        KtorRemoteProjectDataSource(
            mockHttpClient(sent) { request ->
                when (request.method) {
                    HttpMethod.Get -> {
                        respondJson("[$PROJECT_JSON]")
                    }

                    HttpMethod.Delete -> {
                        respondNoContent()
                    }

                    else -> {
                        if (request.url.encodedPath.endsWith(
                                "sort",
                            )
                        ) {
                            respondNoContent()
                        } else {
                            respondJson(PROJECT_JSON)
                        }
                    }
                }
            },
        )

    @Test
    fun readsAndWritesGoToTheProjectRoutes() =
        runTest {
            val titles =
                listOf(
                    dataSource.getProjects().map { list -> list.single().title },
                    dataSource.postProject(project).map { it.title },
                    dataSource.updateProject(project).map { it.title },
                )

            assertThat(titles).isEqualTo(List(3) { Result.Success("Garden") })
            assertThat(dataSource.deleteProject("p1")).isEqualTo(Result.Success(Unit))
            assertThat(sent.map { it.method to it.path }).isEqualTo(
                listOf(
                    HttpMethod.Get to "/api/projects",
                    HttpMethod.Post to "/api/projects",
                    HttpMethod.Put to "/api/projects/p1",
                    HttpMethod.Delete to "/api/projects/p1",
                ),
            )
        }

    @Test
    fun aReorderIsOneRequestCarryingEveryIndex() =
        runTest {
            val result =
                dataSource.reorderProjects(
                    mapOf("p1" to 0L, "p2" to 1L),
                    Instant.parse("2026-09-01T10:00:00Z"),
                )

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(sent.single().path).isEqualTo("/api/projects/sort")
            assertThat(sent.single().body).contains("\"projectId\":\"p2\"")
        }

    @Test
    fun aMissingProjectIsReportedAsSuch() =
        runTest {
            val failing = KtorRemoteProjectDataSource(mockHttpClient { respondJson("{}", HttpStatusCode.Forbidden) })

            assertThat(failing.deleteProject("gone")).isEqualTo(Result.Error(DataError.Remote.FORBIDDEN))
        }

    private companion object {
        const val PROJECT_JSON =
            """{"id":"p1","title":"Garden","description":null,"color":null,"totalDuration":null,""" +
                """"startDateTimeUtc":"2026-08-01T09:00:00Z","useLightTextColor":false}"""

        val project =
            Project(
                projectId = "p1",
                title = "Garden",
                description = null,
                colorArgb = null,
                totalDurationMillis = null,
                startDateTimeUtc = Instant.parse("2026-08-01T09:00:00Z"),
                isFinished = false,
                endDateTimeUtc = null,
            )
    }
}
