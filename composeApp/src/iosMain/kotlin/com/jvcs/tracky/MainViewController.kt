package com.jvcs.tracky

import androidx.compose.ui.window.ComposeUIViewController

// Swift calls this by name (MainViewControllerKt.MainViewController()); it names the
// UIViewController it builds, as Compose Multiplatform's template does.
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App() }
