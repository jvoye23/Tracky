package com.jvcs.tracky.designsystem.util

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DeviceConfigurationTest {

    // Same bucketing currentWindowAdaptiveInfo() applies to the raw window size.
    private fun configurationFor(widthDp: Float, heightDp: Float): DeviceConfiguration =
        DeviceConfiguration.fromWindowSizeClass(
            WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(widthDp, heightDp),
        )

    @Test
    fun aPhoneHeldUprightIsMobilePortrait() {
        assertThat(configurationFor(412f, 924f)).isEqualTo(DeviceConfiguration.MOBILE_PORTRAIT)
    }

    @Test
    fun aLargePhoneInLandscapeIsMobileLandscape() {
        assertThat(configurationFor(924f, 412f)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun aSmallPhoneInLandscapeIsMobileLandscapeNotDesktop() {
        // 732dp wide lands in the medium width bucket, which used to fall through to DESKTOP.
        assertThat(configurationFor(732f, 360f)).isEqualTo(DeviceConfiguration.MOBILE_LANDSCAPE)
    }

    @Test
    fun aTabletHeldUprightIsTabletPortrait() {
        assertThat(configurationFor(800f, 1280f)).isEqualTo(DeviceConfiguration.TABLET_PORTRAIT)
    }

    @Test
    fun aTabletInLandscapeIsTabletLandscape() {
        assertThat(configurationFor(1280f, 800f)).isEqualTo(DeviceConfiguration.TABLET_LANDSCAPE)
    }

    @Test
    fun aLargeDesktopWindowIsDesktop() {
        assertThat(configurationFor(1920f, 1080f)).isEqualTo(DeviceConfiguration.DESKTOP)
    }
}
