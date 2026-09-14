package com.jvcs.tracky.core.domain.notification

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun RequestTimerNotificationPermission(request: Boolean) {
    val context = LocalContext.current
    // Survives rotation, so a user who declined is not asked again on every recomposition. Android
    // stops showing the dialog after two refusals anyway; this keeps us from even trying.
    var alreadyAsked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied is a valid answer: the timer still runs, it just has nowhere to show itself. */ }

    LaunchedEffect(request) {
        if (!request || alreadyAsked) return@LaunchedEffect
        alreadyAsked = true
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
