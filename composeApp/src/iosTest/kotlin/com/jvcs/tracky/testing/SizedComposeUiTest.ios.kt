package com.jvcs.tracky.testing

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runSkikoComposeUiTest
import kotlinx.coroutines.test.TestResult

// The v1 runner on purpose: it is what the screen tests were written against (the desktop runner
// they used before the JVM target went away), with compositions dispatched immediately.
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
actual fun runSizedComposeUiTest(
    width: Int,
    height: Int,
    block: suspend ComposeUiTest.() -> Unit,
): TestResult = runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) { block() }
