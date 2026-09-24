package com.plcoding.prism.tooling.detekt

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import dev.detekt.api.Config
import dev.detekt.test.lint
import org.junit.jupiter.api.Test

class ObserveAsEventsRequiredTest {
    private val rule = ObserveAsEventsRequired(Config.empty)

    @Test
    fun `reports a LaunchedEffect collecting a ViewModel event flow`() {
        val code =
            """
            @Composable
            fun LoginRoot(viewModel: LoginViewModel) {
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event -> handle(event) }
                }
            }
            """.trimIndent()

        assertThat(rule.lint(code)).hasSize(1)
    }

    @Test
    fun `does not report a snapshotFlow collection of local Compose state`() {
        val code =
            """
            @Composable
            fun Pager(pagerState: PagerState) {
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { page -> onPage(page) }
                }
            }
            """.trimIndent()

        assertThat(rule.lint(code)).isEmpty()
    }

    @Test
    fun `does not report the ObserveAsEvents implementation itself`() {
        val code =
            """
            @Composable
            fun <T> ObserveAsEvents(flow: Flow<T>, onEvent: (T) -> Unit) {
                LaunchedEffect(flow) { flow.collect(onEvent) }
            }
            """.trimIndent()

        assertThat(rule.lint(code)).isEmpty()
    }
}
