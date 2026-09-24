package com.jvcs.tracky

import androidx.compose.ui.window.ComposeUIViewController

// Swift calls this by name (MainViewControllerKt.MainViewController()); it names the
// UIViewController it builds, as Compose Multiplatform's template does.
@Suppress("FunctionNaming")
fun MainViewController() = ComposeUIViewController { App() }
