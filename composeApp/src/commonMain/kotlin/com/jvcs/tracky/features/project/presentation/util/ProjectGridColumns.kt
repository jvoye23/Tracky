package com.jvcs.tracky.features.project.presentation.util

import com.jvcs.tracky.designsystem.util.DeviceConfiguration

/** Landscape windows fit project cards side by side; portrait ones keep a single column. */
val DeviceConfiguration.projectGridColumns: Int
    get() =
        when (this) {
            DeviceConfiguration.MOBILE_LANDSCAPE,
            DeviceConfiguration.TABLET_LANDSCAPE,
            DeviceConfiguration.DESKTOP,
            -> 2

            DeviceConfiguration.MOBILE_PORTRAIT,
            DeviceConfiguration.TABLET_PORTRAIT,
            -> 1
        }
