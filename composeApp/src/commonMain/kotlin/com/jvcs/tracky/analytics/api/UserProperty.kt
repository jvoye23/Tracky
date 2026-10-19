package com.jvcs.tracky.analytics.api

sealed interface UserProperty {

    val name: String
    val value: String

    data class PlanTier(val tier: PlanTierOption) : UserProperty {

        override val name: String = "plan_tier"
        override val value: String =
            when (tier) {
                PlanTierOption.FREE -> "free"
                PlanTierOption.PRO -> "pro"
            }
    }

    data class ProjectsCount(val projectCount: Int) : UserProperty {

        override val name: String = "projects_count"
        override val value: String =
            when {
                projectCount == 0 -> "0"
                projectCount <= 5 -> "1-5"
                else -> projectCount.toString()
            }
    }

    data class SubTasksCount(val subTaskCount: Int) : UserProperty {

        override val name: String = "sub_tasks_count"
        override val value: String =
            when {
                subTaskCount == 0 -> "0"
                subTaskCount <= 5 -> "1-5"
                else -> subTaskCount.toString()
            }
    }
}
