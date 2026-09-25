package com.jvcs.tracky.features.project.presentation.export

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.designsystem.theme.Inter
import com.jvcs.tracky.designsystem.theme.onSurfaceLight
import com.jvcs.tracky.designsystem.theme.onSurfaceVariantLight
import com.jvcs.tracky.designsystem.theme.outlineLight
import com.jvcs.tracky.designsystem.theme.reportDefaultAccent
import com.jvcs.tracky.designsystem.theme.reportFinishedLight
import com.jvcs.tracky.designsystem.theme.reportRuleLight
import com.jvcs.tracky.designsystem.theme.surfaceContainerLight
import org.jetbrains.compose.resources.Font
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.roboto_mono_variable

private const val TITLE_TRACKING = -0.5f

/**
 * The look of an exported report page: [accent] is the project's color and becomes `primary`.
 *
 * Light only, whatever the device is set to — a printed or shared page has no dark mode, and its
 * reader's viewer will not invert it. Blocks read everything from [MaterialTheme]; the slots map to:
 * - colors: `outline` grey labels, `outlineVariant` rules and borders, `surfaceVariant` bar tracks,
 *   `tertiary` the "Finished" green.
 * - Inter text: `headlineLarge` project title, `headlineSmall` summary figures, `titleLarge`
 *   wordmark, `titleMedium` section headings, `titleSmall` table titles, `bodyMedium` description,
 *   `bodySmall` summary notes, `labelMedium` task status, `labelSmall` spaced caps labels.
 * - Roboto Mono: `displayMedium` total tracked, `displaySmall` table durations, `bodyLarge` table
 *   counts, `labelLarge` dates, color code and share percentages.
 */
@Composable
internal fun ReportTheme(accent: Color = reportDefaultAccent, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            lightColorScheme(
                primary = accent,
                onPrimary = Color.White,
                background = Color.White,
                onBackground = onSurfaceLight,
                surface = Color.White,
                onSurface = onSurfaceLight,
                onSurfaceVariant = onSurfaceVariantLight,
                surfaceVariant = surfaceContainerLight,
                outline = outlineLight,
                outlineVariant = reportRuleLight,
                tertiary = reportFinishedLight,
            ),
        typography = reportTypography(),
        content = content,
    )
}

@Composable
private fun reportTypography(): Typography {
    val inter = Inter
    val mono =
        FontFamily(
            Font(Res.font.roboto_mono_variable, FontWeight.Normal),
            Font(Res.font.roboto_mono_variable, FontWeight.Medium),
            Font(Res.font.roboto_mono_variable, FontWeight.Bold),
        )

    fun style(
        family: FontFamily,
        weight: FontWeight,
        size: TextUnit,
        letterSpacing: TextUnit = 0.sp,
    ) = TextStyle(fontFamily = family, fontWeight = weight, fontSize = size, letterSpacing = letterSpacing)

    return Typography(
        headlineLarge = style(inter, FontWeight.Bold, 29.sp, TITLE_TRACKING.sp),
        headlineSmall = style(inter, FontWeight.Medium, 17.sp),
        titleLarge = style(inter, FontWeight.Bold, 11.5.sp),
        titleMedium = style(inter, FontWeight.Bold, 13.sp),
        titleSmall = style(inter, FontWeight.SemiBold, 9.5.sp),
        bodyMedium = style(inter, FontWeight.Normal, 10.5.sp),
        bodySmall = style(inter, FontWeight.Medium, 8.sp),
        labelMedium = style(inter, FontWeight.SemiBold, 8.5.sp),
        labelSmall = style(inter, FontWeight.SemiBold, 7.25.sp, 1.35.sp),
        displayMedium = style(mono, FontWeight.Medium, 16.sp),
        displaySmall = style(mono, FontWeight.Bold, 9.5.sp),
        bodyLarge = style(mono, FontWeight.Normal, 9.sp),
        labelLarge = style(mono, FontWeight.Normal, 8.5.sp),
    )
}
