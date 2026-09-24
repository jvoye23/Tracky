package com.jvcs.tracky.features.project.presentation.projectdetail.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jvcs.tracky.designsystem.theme.SampleProjectColors
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.theme.rgbSliderBlue
import com.jvcs.tracky.designsystem.theme.rgbSliderGreen
import com.jvcs.tracky.designsystem.theme.rgbSliderRed
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.cancel
import tracky.composeapp.generated.resources.hex_code
import tracky.composeapp.generated.resources.select
import tracky.composeapp.generated.resources.select_color
import kotlin.math.roundToInt

private const val CHANNEL_MAX = 255f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackyColorPicker(
    currentColor: Color,
    onCancel: () -> Unit,
    onSave: (Color) -> Unit,
) {
    // Extract initial HSV values using KMP-compatible pure Kotlin
    val hsvArray = currentColor.toHsv()

    // Using separate state variables for H, S, V ensures performant localized recompositions
    var hue by remember { mutableFloatStateOf(hsvArray[0]) }
    var saturation by remember { mutableFloatStateOf(hsvArray[1]) }
    var value by remember { mutableFloatStateOf(hsvArray[2]) }

    val derivedColor = Color.hsv(hue, saturation, value)

    // Pure Kotlin Hex conversion for KMP (Replaces JVM String.format and toArgb)
    val rHex =
        (derivedColor.red * 255)
            .toInt()
            .toString(16)
            .padStart(2, '0')
            .uppercase()
    val gHex =
        (derivedColor.green * 255)
            .toInt()
            .toString(16)
            .padStart(2, '0')
            .uppercase()
    val bHex =
        (derivedColor.blue * 255)
            .toInt()
            .toString(16)
            .padStart(2, '0')
            .uppercase()
    val hexString = "#$rHex$gHex$bHex"

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Spectrum", "Sliders")

    Dialog(onDismissRequest = onCancel) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 24.dp),
            ) {
                // Header
                Text(
                    text = stringResource(Res.string.select_color),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )

                // Preview & Hex Row
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(derivedColor)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )

                    OutlinedTextField(
                        value = hexString,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.hex_code)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }

                // M3 Segmented Button for Tabs
                SingleChoiceSegmentedButtonRow(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    tabs.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = index == selectedTab,
                            onClick = { selectedTab = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        ) {
                            Text(label)
                        }
                    }
                }

                // Dynamic Content Area
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .height(260.dp), // Fixed height to prevent dialog jumping when switching tabs
                ) {
                    when (selectedTab) {
                        0 -> {
                            SpectrumTab(
                                hue = hue,
                                saturation = saturation,
                                value = value,
                                onHueChange = { hue = it },
                                onSaturationValueChange = { newSaturation, newValue ->
                                    saturation = newSaturation
                                    value = newValue
                                },
                            )
                        }

                        1 -> {
                            SlidersTab(
                                currentColor = derivedColor,
                                onColorChange = { newColor ->
                                    val newHsv = newColor.toHsv()
                                    hue = newHsv[0]
                                    saturation = newHsv[1]
                                    value = newHsv[2]
                                },
                            )
                        }
                    }
                }

                // Actions
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(Res.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSave(derivedColor) }) {
                        Text(text = stringResource(Res.string.select))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumTab(
    hue: Float,
    saturation: Float,
    value: Float,
    onHueChange: (Float) -> Unit,
    onSaturationValueChange: (Float, Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 2D Saturation/Value Canvas
        val pureHueColor = Color.hsv(hue, 1f, 1f)
        val selectedColor = Color.hsv(hue, saturation, value)

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newS = (change.position.x / size.width).coerceIn(0f, 1f)
                            val newV = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onSaturationValueChange(newS, newV)
                        }
                    }.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newS = (offset.x / size.width).coerceIn(0f, 1f)
                            val newV = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            onSaturationValueChange(newS, newV)
                        }
                    },
        ) {
            // Draw pure hue background
            drawRect(color = pureHueColor)

            // Draw white to transparent (left to right) - Saturation
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        colors = listOf(Color.White, Color.Transparent),
                    ),
            )

            // Draw black to transparent (bottom to top) - Value
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                    ),
            )

            // Draw Thumb
            val thumbX = saturation * size.width
            val thumbY = (1f - value) * size.height

            drawCircle(
                color = selectedColor,
                radius = 12.dp.toPx(),
                center = Offset(thumbX, thumbY),
            )
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Custom Hue Slider
        val rainbowColors =
            listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red,
            )

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp) // Extra height for the thumb bounding box
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newHue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            onHueChange(newHue)
                        }
                    }.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                            onHueChange(newHue)
                        }
                    },
        ) {
            // Draw rainbow track
            val trackHeight = 12.dp.toPx()
            val trackY = (size.height - trackHeight) / 2
            drawRoundRect(
                brush = Brush.horizontalGradient(rainbowColors),
                topLeft = Offset(0f, trackY),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )

            // Draw Hue Thumb
            val thumbX = (hue / 360f) * size.width
            drawCircle(
                color = pureHueColor,
                radius = 10.dp.toPx(),
                center = Offset(thumbX, size.height / 2),
            )
            drawCircle(
                color = Color.White,
                radius = 10.dp.toPx(),
                center = Offset(thumbX, size.height / 2),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun SlidersTab(currentColor: Color, onColorChange: (Color) -> Unit) {
    val redChannel = (currentColor.red * CHANNEL_MAX).roundToInt()
    val greenChannel = (currentColor.green * CHANNEL_MAX).roundToInt()
    val blueChannel = (currentColor.blue * CHANNEL_MAX).roundToInt()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        SliderRow(
            label = "R",
            value = redChannel,
            activeColor = rgbSliderRed,
            onValueChange = { newVal ->
                onColorChange(Color(newVal / CHANNEL_MAX, currentColor.green, currentColor.blue, currentColor.alpha))
            },
        )
        SliderRow(
            label = "G",
            value = greenChannel,
            activeColor = rgbSliderGreen,
            onValueChange = { newVal ->
                onColorChange(Color(currentColor.red, newVal / CHANNEL_MAX, currentColor.blue, currentColor.alpha))
            },
        )
        SliderRow(
            label = "B",
            value = blueChannel,
            activeColor = rgbSliderBlue,
            onValueChange = { newVal ->
                onColorChange(Color(currentColor.red, currentColor.green, newVal / CHANNEL_MAX, currentColor.alpha))
            },
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    activeColor: Color,
    onValueChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
            colors =
                SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
        )

        Box(
            modifier =
                Modifier
                    .width(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Pure Kotlin KMP-compatible extension to convert a Compose Color to an HSV array.
 * Returns floatArrayOf(hue, saturation, value).
 */
private fun Color.toHsv(): FloatArray {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min

    val hue =
        when {
            max == min -> 0f
            max == red -> (60f * ((green - blue) / delta) + 360f) % 360f
            max == green -> (60f * ((blue - red) / delta) + 120f)
            else -> (60f * ((red - green) / delta) + 240f)
        }

    val saturation = if (max == 0f) 0f else delta / max
    val value = max

    return floatArrayOf(hue, saturation, value)
}

@Preview(showBackground = true)
@Composable
private fun TrackyColorPickerPreview() {
    TrackyTheme {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TrackyColorPicker(
                currentColor = SampleProjectColors.Slate,
                onCancel = {},
                onSave = {},
            )
        }
    }
}
