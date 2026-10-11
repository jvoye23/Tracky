package com.jvcs.tracky.testing

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.TestResult

// A device's window cannot be resized per test, so the content gets a window of its own: the
// adaptive layout reads LocalWindowInfo, and the box lays the content out at that size. The v1
// runner on purpose, like the iOS one: compositions are dispatched immediately.
@Suppress("DEPRECATION")
@OptIn(ExperimentalTestApi::class)
actual fun runSizedComposeUiTest(
    width: Int,
    height: Int,
    block: suspend ComposeUiTest.() -> Unit,
): TestResult =
    runComposeUiTest {
        val sized =
            object : ComposeUiTest by this {
                override fun setContent(composable: @Composable () -> Unit) =
                    this@runComposeUiTest.setContent { SizedWindow(width, height, composable) }
            }
        sized.block()
    }

@Composable
private fun SizedWindow(
    width: Int,
    height: Int,
    content: @Composable () -> Unit,
) {
    val outer = LocalWindowInfo.current
    val window =
        object : WindowInfo by outer {
            override val containerSize = IntSize(width, height)
        }
    CompositionLocalProvider(LocalDensity provides Density(1f), LocalWindowInfo provides window) {
        Box(Modifier.requiredSize(width.dp, height.dp)) { content() }
    }
}
