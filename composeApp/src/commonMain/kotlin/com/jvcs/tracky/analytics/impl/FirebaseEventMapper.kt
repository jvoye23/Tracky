package com.jvcs.tracky.analytics.impl

import com.jvcs.tracky.analytics.api.AnalyticsEvent
import dev.gitlive.firebase.analytics.FirebaseAnalyticsEvents
import dev.gitlive.firebase.analytics.FirebaseAnalyticsParam

fun AnalyticsEvent.toFirebaseEvent(): FirebaseEvent =
    when (this) {
        is AnalyticsEvent.OnboardingCompleted -> {
            FirebaseEvent(
                name = FirebaseAnalyticsEvents.TUTORIAL_COMPLETE,
                params = mapOf("steps_completed" to stepsCompleted.toLong()),
            )
        }

        is AnalyticsEvent.FirstProjectCreated -> {
            FirebaseEvent(
                name = FirebaseAnalyticsEvents.LEVEL_START,
                params = mapOf("seconds_since_install" to secondsSinceInstall.toDouble()),
            )
        }

        is AnalyticsEvent.ProjectOverviewScreenViewed -> {
            FirebaseEvent(
                name = FirebaseAnalyticsEvents.SCREEN_VIEW,
                params =
                    mapOf(
                        FirebaseAnalyticsParam.SCREEN_NAME to screenName,
                        FirebaseAnalyticsParam.SCREEN_CLASS to screenName,
                    ),
            )
        }

        is AnalyticsEvent.TaskDetailScreenViewed -> {
            FirebaseEvent(
                name = FirebaseAnalyticsEvents.SCREEN_VIEW,
                params =
                    mapOf(
                        FirebaseAnalyticsParam.SCREEN_NAME to screenName,
                        FirebaseAnalyticsParam.SCREEN_CLASS to screenName,
                    ),
            )
        }

        // No standard Firebase event fits an export, so it goes under its own name.
        AnalyticsEvent.PdfExported -> {
            FirebaseEvent(name = name, params = params)
        }
    }
