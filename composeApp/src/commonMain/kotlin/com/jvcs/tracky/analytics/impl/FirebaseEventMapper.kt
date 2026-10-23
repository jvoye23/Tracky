package com.jvcs.tracky.analytics.impl

import com.jvcs.tracky.analytics.api.AnalyticsEvent

// Firebase's reserved event and parameter names, spelled out: common code no longer sees an SDK
// that would name them, and both platforms' SDKs accept the raw strings.
private const val EVENT_TUTORIAL_COMPLETE = "tutorial_complete"
private const val EVENT_LEVEL_START = "level_start"
private const val EVENT_SCREEN_VIEW = "screen_view"
private const val PARAM_SCREEN_NAME = "screen_name"
private const val PARAM_SCREEN_CLASS = "screen_class"

fun AnalyticsEvent.toFirebaseEvent(): FirebaseEvent =
    when (this) {
        is AnalyticsEvent.OnboardingCompleted -> {
            FirebaseEvent(
                name = EVENT_TUTORIAL_COMPLETE,
                params = mapOf("steps_completed" to stepsCompleted.toLong()),
            )
        }

        is AnalyticsEvent.FirstProjectCreated -> {
            FirebaseEvent(
                name = EVENT_LEVEL_START,
                params = mapOf("seconds_since_install" to secondsSinceInstall.toDouble()),
            )
        }

        is AnalyticsEvent.ProjectOverviewScreenViewed -> {
            FirebaseEvent(
                name = EVENT_SCREEN_VIEW,
                params =
                    mapOf(
                        PARAM_SCREEN_NAME to screenName,
                        PARAM_SCREEN_CLASS to screenName,
                    ),
            )
        }

        is AnalyticsEvent.TaskDetailScreenViewed -> {
            FirebaseEvent(
                name = EVENT_SCREEN_VIEW,
                params =
                    mapOf(
                        PARAM_SCREEN_NAME to screenName,
                        PARAM_SCREEN_CLASS to screenName,
                    ),
            )
        }

        // No standard Firebase event fits an export, so it goes under its own name.
        AnalyticsEvent.PdfExported -> {
            FirebaseEvent(name = name, params = params)
        }
    }
