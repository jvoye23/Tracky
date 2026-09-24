package com.jvcs.tracky.core.data.timer

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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

            assertIs<Result.Success<*>>(result)
            assertNull(result.data)
        }

    @Test
    fun anOpenIntervalComesBackAsTheActiveTimer() =
        runTest {
            val result = dataSource(HttpStatusCode.OK, activeBody).getActive()

            assertIs<Result.Success<*>>(result)
            assertEquals(
                "9d42d176-0000-4000-8000-000000000001",
                (result.data as com.jvcs.tracky.core.domain.timer.ActiveTimer).intervalId,
            )
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

            assertIs<Result.Success<*>>(result)
            val applied = assertIs<ActiveTimerChange.Applied>(result.data)
            assertEquals("c6df0a86-0000-4000-8000-000000000004", applied.touchedTaskIntervals.single().intervalId)
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

            assertIs<Result.Success<*>>(result)
            val rejected = assertIs<ActiveTimerChange.Rejected>(result.data)
            assertEquals("9d42d176-0000-4000-8000-000000000001", rejected.active?.intervalId)
        }

    @Test
    fun aConflictWithNoReadableBodyIsStillARefusal() =
        runTest {
            // Retrying would repeat a request the server has already ruled on, so an unreadable 409
            // must not degrade into a transport error the queue would send again. A proxy's HTML
            // error page is the realistic way to get one.
            val result = dataSource(HttpStatusCode.Conflict, "<html>gateway said no</html>").start(start)

            assertIs<Result.Success<*>>(result)
            val rejected = assertIs<ActiveTimerChange.Rejected>(result.data)
            assertNull(rejected.active)
        }

    @Test
    fun aReplayedStopTouchesNothingAndStillSucceeds() =
        runTest {
            val result =
                dataSource(
                    HttpStatusCode.OK,
                    """{"active":null,"touched":[],"serverNowUtc":"2026-09-21T15:56:16.642Z"}""",
                ).stop("c6df0a86-0000-4000-8000-000000000004", Instant.parse("2026-09-21T15:57:00Z"))

            val applied = assertIs<ActiveTimerChange.Applied>(assertIs<Result.Success<*>>(result).data)
            assertNull(applied.active)
            assertTrue(applied.touchedTaskIntervals.isEmpty())
        }

    @Test
    fun aServerErrorStaysRetryable() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
            val client =
                HttpClient(engine) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }

            assertEquals(
                Result.Error(DataError.Remote.SERVICE_UNAVAILABLE),
                KtorRemoteActiveTimerDataSource(client).getActive(),
            )
        }

    @Test
    fun aForbiddenStartIsReportedAsSuch() =
        runTest {
            // Someone else's task. The API collapses missing and not-yours into 403 on purpose.
            assertEquals(
                Result.Error(DataError.Remote.FORBIDDEN),
                dataSource(HttpStatusCode.Forbidden).start(start),
            )
        }
}
