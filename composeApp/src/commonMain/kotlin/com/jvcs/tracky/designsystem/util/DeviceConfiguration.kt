package com.jvcs.tracky.designsystem.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

@Composable
fun currentDeviceConfiguration(): DeviceConfiguration {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return DeviceConfiguration.fromWindowSizeClass(windowSizeClass)
}

enum class DeviceConfiguration {
    MOBILE_PORTRAIT,
    MOBILE_LANDSCAPE,
    TABLET_PORTRAIT,
    TABLET_LANDSCAPE,
    DESKTOP,
    ;

    val isMobile: Boolean
        get() = this in listOf(MOBILE_PORTRAIT, MOBILE_LANDSCAPE)

    val isWideScreen: Boolean
        get() = this in listOf(TABLET_LANDSCAPE, DESKTOP)

    companion object {
        fun fromWindowSizeClass(windowSizeClass: WindowSizeClass): DeviceConfiguration =
            with(windowSizeClass) {
                when {
                    minWidthDp < WIDTH_DP_MEDIUM_LOWER_BOUND &&
                        minHeightDp >= HEIGHT_DP_MEDIUM_LOWER_BOUND -> MOBILE_PORTRAIT

                    // Medium (not expanded) lower bound: smaller phones in landscape land in the
                    // 600–839dp width bucket and must not fall through to DESKTOP.
                    minWidthDp >= WIDTH_DP_MEDIUM_LOWER_BOUND &&
                        minHeightDp < HEIGHT_DP_MEDIUM_LOWER_BOUND -> MOBILE_LANDSCAPE

                    // Exclusive upper bounds: bucketed sizes sit exactly on 840/900, and an inclusive
                    // range would classify a large desktop window (840x900 bucket) as a tablet.
                    minWidthDp in WIDTH_DP_MEDIUM_LOWER_BOUND until WIDTH_DP_EXPANDED_LOWER_BOUND &&
                        minHeightDp >= HEIGHT_DP_EXPANDED_LOWER_BOUND -> TABLET_PORTRAIT

                    minWidthDp >= WIDTH_DP_EXPANDED_LOWER_BOUND &&
                        minHeightDp in
                        HEIGHT_DP_MEDIUM_LOWER_BOUND until HEIGHT_DP_EXPANDED_LOWER_BOUND -> TABLET_LANDSCAPE

                    else -> DESKTOP
                }
            }
    }
}
