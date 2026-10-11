package com.jvcs.tracky.features.onboarding.presentation.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.jvcs.tracky.designsystem.theme.FeatureAccentColors
import org.jetbrains.compose.resources.StringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.basic_feature_background_description
import tracky.composeapp.generated.resources.basic_feature_background_title
import tracky.composeapp.generated.resources.basic_feature_calendar_description
import tracky.composeapp.generated.resources.basic_feature_calendar_title
import tracky.composeapp.generated.resources.basic_feature_offline_description
import tracky.composeapp.generated.resources.basic_feature_offline_title
import tracky.composeapp.generated.resources.basic_feature_projects_description
import tracky.composeapp.generated.resources.basic_feature_projects_title
import tracky.composeapp.generated.resources.basic_feature_reorder_description
import tracky.composeapp.generated.resources.basic_feature_reorder_title
import tracky.composeapp.generated.resources.basic_feature_stats_description
import tracky.composeapp.generated.resources.basic_feature_stats_title
import tracky.composeapp.generated.resources.basic_feature_tasks_description
import tracky.composeapp.generated.resources.basic_feature_tasks_title
import tracky.composeapp.generated.resources.pro_feature_archive_description
import tracky.composeapp.generated.resources.pro_feature_archive_title
import tracky.composeapp.generated.resources.pro_feature_devices_description
import tracky.composeapp.generated.resources.pro_feature_devices_title
import tracky.composeapp.generated.resources.pro_feature_export_description
import tracky.composeapp.generated.resources.pro_feature_export_title
import tracky.composeapp.generated.resources.pro_feature_pin_description
import tracky.composeapp.generated.resources.pro_feature_pin_title
import tracky.composeapp.generated.resources.pro_feature_stats_description
import tracky.composeapp.generated.resources.pro_feature_stats_title
import tracky.composeapp.generated.resources.pro_feature_subtasks_description
import tracky.composeapp.generated.resources.pro_feature_subtasks_title
import tracky.composeapp.generated.resources.pro_feature_sync_description
import tracky.composeapp.generated.resources.pro_feature_sync_title
import tracky.composeapp.generated.resources.pro_feature_unlimited_description
import tracky.composeapp.generated.resources.pro_feature_unlimited_title

/** One card in a feature list. Fixed copy, so it is resources rather than ViewModel state. */
data class OnboardingFeature(
    val icon: ImageVector,
    val title: StringResource,
    val description: StringResource,
)

/** What the free version offers, in the order the requirements list it. */
val basicFeatures: List<OnboardingFeature> =
    listOf(
        OnboardingFeature(
            Icons.Outlined.PhoneAndroid,
            Res.string.basic_feature_offline_title,
            Res.string.basic_feature_offline_description,
        ),
        OnboardingFeature(
            Icons.Outlined.FolderOpen,
            Res.string.basic_feature_projects_title,
            Res.string.basic_feature_projects_description,
        ),
        OnboardingFeature(
            Icons.Outlined.Checklist,
            Res.string.basic_feature_tasks_title,
            Res.string.basic_feature_tasks_description,
        ),
        OnboardingFeature(
            Icons.Outlined.Timer,
            Res.string.basic_feature_background_title,
            Res.string.basic_feature_background_description,
        ),
        OnboardingFeature(
            Icons.Outlined.SwapVert,
            Res.string.basic_feature_reorder_title,
            Res.string.basic_feature_reorder_description,
        ),
        OnboardingFeature(
            Icons.Outlined.CalendarMonth,
            Res.string.basic_feature_calendar_title,
            Res.string.basic_feature_calendar_description,
        ),
        OnboardingFeature(
            Icons.Outlined.BarChart,
            Res.string.basic_feature_stats_title,
            Res.string.basic_feature_stats_description,
        ),
    )

/** What Pro adds on top of [basicFeatures]. */
val proFeatures: List<OnboardingFeature> =
    listOf(
        OnboardingFeature(
            Icons.Outlined.CloudSync,
            Res.string.pro_feature_sync_title,
            Res.string.pro_feature_sync_description,
        ),
        OnboardingFeature(
            Icons.Outlined.Devices,
            Res.string.pro_feature_devices_title,
            Res.string.pro_feature_devices_description,
        ),
        OnboardingFeature(
            Icons.Outlined.AllInclusive,
            Res.string.pro_feature_unlimited_title,
            Res.string.pro_feature_unlimited_description,
        ),
        OnboardingFeature(
            Icons.Outlined.AccountTree,
            Res.string.pro_feature_subtasks_title,
            Res.string.pro_feature_subtasks_description,
        ),
        OnboardingFeature(
            Icons.Outlined.Archive,
            Res.string.pro_feature_archive_title,
            Res.string.pro_feature_archive_description,
        ),
        OnboardingFeature(
            Icons.Outlined.IosShare,
            Res.string.pro_feature_export_title,
            Res.string.pro_feature_export_description,
        ),
        OnboardingFeature(
            Icons.Outlined.Insights,
            Res.string.pro_feature_stats_title,
            Res.string.pro_feature_stats_description,
        ),
        OnboardingFeature(
            Icons.Outlined.PushPin,
            Res.string.pro_feature_pin_title,
            Res.string.pro_feature_pin_description,
        ),
    )

/** Accent per feature card, by position; cycles if a list is ever longer than eight. */
val listOfColors: List<Color> =
    listOf(
        FeatureAccentColors.Blue,
        FeatureAccentColors.Green,
        FeatureAccentColors.Orange,
        FeatureAccentColors.Purple,
        FeatureAccentColors.Pink,
        FeatureAccentColors.Teal,
        FeatureAccentColors.Amber,
        FeatureAccentColors.Indigo,
    )
