package com.jvcs.tracky.core.data.timer

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.jvcs.tracky.core.domain.timer.ActiveTimerChange
import com.jvcs.tracky.core.domain.timer.ActiveTimerKind
import com.jvcs.tracky.core.domain.timer.StartActiveTimer
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.time.Instant

/**
 * The statuses of `/api/timer/active` carry meaning, which is why this data source does not use
 * the shared `get`/`put`/`post` helpers: a `204` is an answer and a `409` has a body worth
 * reading. Those two are what this test is for.
 */
class KtorRemoteActiveTimerDataSourceTest {

    private fun dataSource(status: HttpStatusCode, body: String = ""): KtorRemoteActiveTimerDataSource {
        val engine =
            MockEngine {
                if (body.isEmpty()) {
                    respond(content = "", status = status)
                } else {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            }
        // Mirrors HttpClientFactory's negotiation config; auth and logging are irrelevant here.
        val client =
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        return KtorRemoteActiveTimerDataSource(client)
    }

    private val start =
        StartActiveTimer(
            intervalId = "c6df0a86-0000-4000-8000-000000000004",
            kind = ActiveTimerKind.TASK,
            parentTaskId = "7947002a-0000-4000-8000-000000000003",
            parentSubTaskId = null,
            parentTaskIntervalId = null,
            startedAt = Instant.parse("2026-09-21T15:56:14.425Z"),
            deviceId = "25247336-0000-4000-8000-00000000000a",
        )

    private val activeBody =
        """
        {
          "intervalId": "9d42d176-0000-4000-8000-000000000001",
          "kind": "task",
          "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
          "parentTaskId": "7947002a-0000-4000-8000-000000000003",
          "startedAtUtc": "2026-09-21T15:56:16.284Z",
          "startedByDeviceId": "25247336-0000-4000-8000-00000000000a"
        }
        """.trimIndent()

    @Test
    fun noContentMeansNothingIsRunningRatherThanAFailedDecode() =
        runTest {
            // The shared helper would call body<T>() on an empty payload and report SERIALIZATION.
            // "Nothing is running" is as authoritative an answer as naming an interval.
            val result = dataSource(HttpStatusCode.NoContent).getActive()

            check(result is Result.Success<*>)
            assertThat(result.data).isNull()
        }

    @Test
    fun anOpenIntervalComesBackAsTheActiveTimer() =
        runTest {
            val result = dataSource(HttpStatusCode.OK, activeBody).getActive()

            check(result is Result.Success<*>)
            assertThat(
                (result.data as com.jvcs.tracky.core.domain.timer.ActiveTimer).intervalId,
            ).isEqualTo("9d42d176-0000-4000-8000-000000000001")
        }

    @Test
    fun aStartThatSucceedsCarriesTheRowsItTouched() =
        runTest {
            val result =
                dataSource(
                    HttpStatusCode.OK,
                    """
                    {
                      "active": $activeBody,
                      "touched": [
                        {
                          "kind": "task",
                          "id": "c6df0a86-0000-4000-8000-000000000004",
                          "parentProjectId": "fcd1b6fd-0000-4000-8000-000000000002",
                          "parentTaskId": "7947002a-0000-4000-8000-000000000003",
                          "startDateTimeUtc": "2026-09-21T15:56:14.425Z",
                          "durationMillis": 0
                        }
                      ],
                      "serverNowUtc": "2026-09-21T15:56:16.642Z"
                    }
                    """.trimIndent(),
                ).start(start)

            check(result is Result.Success<*>)
            val applied = result.data
            check(applied is ActiveTimerChange.Applied)
            assertThat(
                applied.touchedTaskIntervals.single().intervalId,
            ).isEqualTo("c6df0a86-0000-4000-8000-000000000004")
        }

    @Test
    fun aConflictIsAnAnswerNotAnError() =
        runTest {
            // The compare-and-swap lost. Reporting this as Result.Error would throw away the timer the
            // body names and cost a second round trip to learn what is actually running.
            val result =
                dataSource(
                    HttpStatusCode.Conflict,
                    """{"code":"TIMER_CONFLICT","message":"not the running timer","active":$activeBody}""",
                ).stop("c6df0a86-0000-4000-8000-000000000004", Instant.parse("2026-09-21T15:57:00Z"))

            check(result is Result.Success<*>)
            val rejected = result.data
            check(rejected is ActiveTimerChange.Rejected)
            assertThat(rejected.active?.intervalId).isEqualTo("9d42d176-0000-4000-8000-000000000001")
        }

    @Test
    fun aConflictWithNoReadableBodyIsStillARefusal() =
        runTest {
            // Retrying would repeat a request the server has already ruled on, so an unreadable 409
            // must not degrade into a transport error the queue would send again. A proxy's HTML
            // error page is the realistic way to get one.
            val result = dataSource(HttpStatusCode.Conflict, "<html>gateway said no</html>").start(start)

            check(result is Result.Success<*>)
            val rejected = result.data
            check(rejected is ActiveTimerChange.Rejected)
            assertThat(rejected.active).isNull()
        }

    @Test
    fun aReplayedStopTouchesNothingAndStillSucceeds() =
        runTest {
            val result =
                dataSource(
                    HttpStatusCode.OK,
                    """{"active":null,"touched":[],"serverNowUtc":"2026-09-21T15:56:16.642Z"}""",
                ).stop("c6df0a86-0000-4000-8000-000000000004", Instant.parse("2026-09-21T15:57:00Z"))

            check(result is Result.Success<*>)

            val applied = result.data

            check(applied is ActiveTimerChange.Applied)
            assertThat(applied.active).isNull()
            assertThat(applied.touchedTaskIntervals.isEmpty()).isTrue()
        }

    @Test
    fun aServerErrorStaysRetryable() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }

            assertThat(
                KtorRemoteActiveTimerDataSource(client).getActive(),
            ).isEqualTo(Result.Error(DataError.Remote.SERVICE_UNAVAILABLE))
        }

    @Test
    fun aForbiddenStartIsReportedAsSuch() =
        runTest {
            // Someone else's task. The API collapses missing and not-yours into 403 on purpose.
            assertThat(
                dataSource(HttpStatusCode.Forbidden).start(start),
            ).isEqualTo(Result.Error(DataError.Remote.FORBIDDEN))
        }
}
