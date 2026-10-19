package com.jvcs.tracky.analytics.api

sealed interface AnalyticsEvent {

    val name: String
    val params: Map<String, Any>

    data class OnboardingCompleted(val stepsCompleted: Int) : AnalyticsEvent {

        override val name: String = "onboarding_completed"
        override val params: Map<String, Any> = mapOf("steps_completed" to stepsCompleted)
    }

    data class FirstProjectCreated(val secondsSinceInstall: Long) : AnalyticsEvent {

        override val name: String = "first_project_created"
        override val params: Map<String, Any> = mapOf("seconds_since_install" to secondsSinceInstall)
    }

    data class ProjectOverviewScreenViewed(val screenName: String) : AnalyticsEvent {

        override val name: String = "project_overview_screen_viewed"
        override val params: Map<String, Any> = mapOf("screen_name" to screenName)
    }

    data class TaskDetailScreenViewed(val screenName: String) : AnalyticsEvent {

        override val name: String = "task_detail_screen_viewed"
        override val params: Map<String, Any> = mapOf("screen_name" to screenName)
    }

    /** A project's PDF report reached the share sheet. */
    data object PdfExported : AnalyticsEvent {

        override val name: String = "pdf_exported"
        override val params: Map<String, Any> = emptyMap()
    }
}
