package com.jvcs.tracky.design_system.util

import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview annotation that renders a composable across phone and tablet
 * form factors in both portrait and landscape orientation.
 */
@Preview(name = "Pixel 9 Pro · Portrait", showSystemUi = true, device = "spec:parent=pixel_9_pro")
@Preview(name = "Pixel 9 Pro · Landscape", showSystemUi = true, device = "spec:parent=pixel_9_pro,orientation=landscape")
@Preview(name = "Pixel Tablet · Portrait", showSystemUi = true, device = "spec:parent=pixel_tablet")
@Preview(name = "Pixel Tablet · Landscape", showSystemUi = true, device = "spec:parent=pixel_tablet,orientation=landscape")
annotation class DevicePreviews
