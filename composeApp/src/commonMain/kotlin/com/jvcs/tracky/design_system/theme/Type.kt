package com.jvcs.tracky.design_system.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.inter_medium
import tracky.composeapp.generated.resources.inter_regular
import tracky.composeapp.generated.resources.inter_semibold
import tracky.composeapp.generated.resources.roboto_mono_variable


val RobotoMono @Composable get() = FontFamily(
    Font(
        Res.font.roboto_mono_variable,
        weight = FontWeight.Normal
    )
)

val Inter @Composable get() = FontFamily(
    Font(
        Res.font.inter_regular,
        weight = FontWeight.Normal
    ),
    Font(
        Res.font.inter_semibold,
        weight = FontWeight.SemiBold
    ),
    Font(
        Res.font.inter_medium,
        weight = FontWeight.Medium
    )
)

val Typography @Composable get() = Typography(
    headlineLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

val Typography.timerStyle: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = RobotoMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp
    )

val Typography.labelXSmall: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 12.sp
    )

val Typography.headlineXSmall: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

val Typography.errorLabel: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 12.sp
    )
val Typography.agendaItemFinished: TextStyle
    @Composable
    get() = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        textDecoration = TextDecoration.LineThrough
    )