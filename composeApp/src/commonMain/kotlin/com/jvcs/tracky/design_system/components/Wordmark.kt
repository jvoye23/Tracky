package com.jvcs.tracky.design_system.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.painterResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.tracky_icon

enum class WordmarkSize { Md, Lg }

@Composable
fun Wordmark(
    size: WordmarkSize = WordmarkSize.Md,
    modifier: Modifier = Modifier,
) {
    val iconBoxSize: Dp = if (size == WordmarkSize.Lg) 44.dp else 32.dp
    val iconSize: Dp = iconBoxSize * 0.55f
    val textStyle: TextStyle = if (size == WordmarkSize.Lg) {
        MaterialTheme.typography.headlineLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            letterSpacing = (-0.5).sp,
        )
    } else {
        MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            letterSpacing = (-0.5).sp,
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(iconBoxSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.tracky_icon),
                contentDescription = null,
                modifier = Modifier.size(50.dp)

            )
        }
        Text(
            text = "Tracky",
            color = MaterialTheme.colorScheme.primary,
            style = textStyle,
        )
    }
}
