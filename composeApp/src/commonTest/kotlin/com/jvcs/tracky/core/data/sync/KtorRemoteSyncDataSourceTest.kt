package com.jvcs.tracky.core.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import com.jvcs.tracky.features.project.domain.models.testInterval
import com.jvcs.tracky.features.project.domain.models.testProject
import com.jvcs.tracky.features.project.domain.models.testSubTask
import com.jvcs.tracky.features.project.domain.models.testTask
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

/**
 * The import posts the same nested shape `GET /api/projects` returns, with the fields a push leaves
 * out; the pull asks for everything after its cursor.
 */
class KtorRemoteSyncDataSourceTest {

    private var request: HttpRequestData? = null
    private var requestBody: String = ""

    private fun dataSource(status: HttpStatusCode = HttpStatusCode.OK): KtorRemoteSyncDataSource {
        val engine =
            MockEngine { data ->
                request = data
                requestBody = data.body.toByteArray().decodeToString()
                respond(
                    content = """{"cursor":42,"serverNowUtc":"2026-10-08T10:00:00Z"}""",
                    status = status,
                    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                )
            }
        // Mirrors HttpClientFactory's negotiation and default content type; auth and logging are
        // irrelevant here.
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                defaultRequest { contentType(ContentType.Application.Json) }
            }
        return KtorRemoteSyncDataSource(client)
    }

    private val project =
        testProject(
            "p1",
            tasks =
                listOf(
                    testTask(
                        "t1",
                        "p1",
                        sortIndex = 3,
                        intervals = listOf(testInterval("i1", "t1", "p1", startedByDeviceId = "device-a")),
                        subTasks = listOf(testSubTask("s1", "t1", "p1", sortIndex = 1)),
                    ),
                ),
        )

    @Test
    fun theWholeTreeIsPostedToTheImportRoute() =
        runTest {
            val result = dataSource().importProjects(listOf(project))

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat(request!!.method).isEqualTo(HttpMethod.Post)
            assertThat(request!!.url.encodedPath).isEqualTo("/api/sync/import")

            val body = Json.parseToJsonElement(requestBody).jsonArray
            val sentProject = body.single().jsonObject
            val sentTask = (sentProject["tasks"] as JsonArray).single().jsonObject
            assertThat(sentProject["id"]!!.jsonPrimitive.content).isEqualTo("p1")
            assertThat(sentTask["sortIndex"]!!.jsonPrimitive.content).isEqualTo("3")
            assertThat(
                (sentTask["intervals"] as JsonArray)
                    .single()
                    .jsonObject["startedByDeviceId"]!!
                    .jsonPrimitive.content,
            ).isEqualTo("device-a")
            assertThat(
                ((sentTask["subTasks"] as JsonArray).single() as JsonObject)["sortIndex"]!!.jsonPrimitive.content,
            ).isEqualTo("1")
        }

    @Test
    fun aRefusalIsReportedAsAnError() =
        runTest {
            val result = dataSource(HttpStatusCode.PayloadTooLarge).importProjects(listOf(project))

            assertThat(result).isEqualTo(Result.Error(DataError.Remote.PAYLOAD_TOO_LARGE))
        }

    @Test
    fun aPullAsksForEverythingAfterItsCursor() =
        runTest {
            val result = dataSource().getChanges(since = 7)

            assertThat(result).isInstanceOf(Result.Success::class)
            assertThat(request!!.method).isEqualTo(HttpMethod.Get)
            assertThat(request!!.url.encodedPath).isEqualTo("/api/sync/changes")
            assertThat(request!!.url.parameters["since"]).isEqualTo("7")
        }

    @Test
    fun aFirstPullSendsNoCursorAtAll() =
        runTest {
            dataSource().getChanges(since = null)

            assertThat(request!!.url.parameters["since"]).isNull()
        }
}
