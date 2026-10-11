package com.jvcs.tracky.testing

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import kotlinx.coroutines.test.TestResult

/**
 * Runs [block] in a test window of [width] by [height] pixels at density 1, so a screen's adaptive
 * layout sees a phone or a tablet. The plain multiplatform runner has no size; the platform one does.
 */
@OptIn(ExperimentalTestApi::class)
expect fun runSizedComposeUiTest(
    width: Int,
    height: Int,
    block: suspend ComposeUiTest.() -> Unit,
): TestResult
