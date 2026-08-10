package com.jvcs.tracky.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.jvcs.tracky.design_system.theme.backgroundDarkMediumContrast

@Composable
fun FullScreenLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundDarkMediumContrast)
            .pointerInput(Unit) {
                // Intercept all touch events to disable the underlying content
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}