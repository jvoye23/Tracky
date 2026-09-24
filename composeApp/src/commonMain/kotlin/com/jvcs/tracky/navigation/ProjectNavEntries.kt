package com.jvcs.tracky.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jvcs.tracky.features.auth.presentation.emailverification.EmailVerificationScreenRoot
import com.jvcs.tracky.features.auth.presentation.emailverification.EmailVerificationViewModel
import com.jvcs.tracky.features.auth.presentation.forgotpassword.ForgotPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.login.LoginScreenRoot
import com.jvcs.tracky.features.auth.presentation.register.RegisterScreenRoot
import com.jvcs.tracky.features.auth.presentation.registersuccess.RegisterSuccessScreenRoot
import com.jvcs.tracky.features.auth.presentation.registersuccess.RegisterSuccessViewModel
import com.jvcs.tracky.features.auth.presentation.resetpassword.ResetPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.resetpassword.ResetPasswordViewModel
import com.jvcs.tracky.features.project.presentation.dailyoverview.DailyOverviewScreenRoot
import com.jvcs.tracky.features.project.presentation.dailyoverview.DailyOverviewViewModel
import com.jvcs.tracky.features.project.presentation.edittext.EditTextScreenRoot
import com.jvcs.tracky.features.project.presentation.edittext.EditTextTarget
import com.jvcs.tracky.features.project.presentation.edittext.EditTextViewModel
import com.jvcs.tracky.features.project.presentation.projectarchive.ProjectArchiveScreenRoot
import com.jvcs.tracky.features.project.presentation.projectarchivedetail.ProjectArchiveDetailScreen
import com.jvcs.tracky.features.project.presentation.projectdetail.ProjectDetailScreenRoot
import com.jvcs.tracky.features.project.presentation.projectdetail.ProjectDetailViewModel
import com.jvcs.tracky.features.project.presentation.projectoverview.ProjectOverviewScreenRoot
import com.jvcs.tracky.features.project.presentation.projecttrash.ProjectTrashScreenRoot
import com.jvcs.tracky.features.project.presentation.taskdetail.TaskDetailScreenRoot
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The project lists: overview, archive and trash. */
fun EntryProviderScope<NavKey>.projectListEntries(backStack: NavBackStack<NavKey>) {
    entry<Route.ProjectRoute.ProjectOverview> {
        ProjectOverviewScreenRoot(
            onNavigateToDetailScreen = { projectId ->
                backStack.add(
                    Route.ProjectRoute.ProjectDetail(
                        isEditMode = false,
                        projectId = projectId,
                    ),
                )
            },
            onNavigateToArchive = {
                backStack.add(Route.ProjectRoute.ProjectArchive)
            },
            onNavigateToTrash = {
                backStack.add(Route.ProjectRoute.ProjectTrash)
            },
            onSuccessfulLogout = {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Login)
            },
        )
    }

    entry<Route.ProjectRoute.ProjectArchive> {
        ProjectArchiveScreenRoot(
            onNavigateToDetail = { projectId ->
                backStack.add(Route.ProjectRoute.ProjectArchiveDetail(projectId))
            },
            onNavigateToProjects = {
                backStack.removeLastOrNull()
            },
            onNavigateToTrash = {
                backStack.removeLastOrNull()
                backStack.add(Route.ProjectRoute.ProjectTrash)
            },
        )
    }

    entry<Route.ProjectRoute.ProjectTrash> {
        ProjectTrashScreenRoot(
            onNavigateToProjects = {
                backStack.removeLastOrNull()
            },
            onNavigateToArchive = {
                backStack.removeLastOrNull()
                backStack.add(Route.ProjectRoute.ProjectArchive)
            },
        )
    }
}

/** A single project, live or archived. */
fun EntryProviderScope<NavKey>.projectDetailEntries(backStack: NavBackStack<NavKey>) {
    entry<Route.ProjectRoute.ProjectArchiveDetail> { key ->
        ProjectArchiveDetailScreen(
            projectId = key.projectId,
            onNavigateBack = {
                backStack.remove(key)
            },
        )
    }

    entry<Route.ProjectRoute.ProjectDetail> { key ->
        val detailVm: ProjectDetailViewModel =
            koinViewModel {
                parametersOf(key.projectId)
            }
        ProjectDetailScreenRoot(
            navigateBack = {
                backStack.remove(key)
            },
            viewModel = detailVm,
            onEditTextClick = { isEditMode, projectId, target, taskId, subTaskId ->
                backStack.add(
                    Route.ProjectRoute.EditTextNavKey(
                        isEditMode = isEditMode,
                        projectId = projectId,
                        target = target,
                        taskId = taskId,
                        subTaskId = subTaskId,
                    ),
                )
            },
            onProjectTaskClick = { sessionId ->
                backStack.add(
                    Route.ProjectRoute.TaskDetail(sessionId),
                )
            },
            onDailyOverviewClick = { epochDay ->
                backStack.add(
                    Route.ProjectRoute.DailyOverview(
                        projectId = key.projectId.orEmpty(),
                        preselectedDateEpochDay = epochDay,
                    ),
                )
            },
        )
    }
}

/** Screens opened from a project: daily overview, task detail, text editing. */
fun EntryProviderScope<NavKey>.projectDetailChildEntries(backStack: NavBackStack<NavKey>) {
    entry<Route.ProjectRoute.DailyOverview> { key ->
        val dailyOverviewVm: DailyOverviewViewModel =
            koinViewModel {
                parametersOf(key.projectId, key.preselectedDateEpochDay)
            }
        DailyOverviewScreenRoot(
            navigateBack = {
                backStack.remove(key)
            },
            viewModel = dailyOverviewVm,
        )
    }

    entry<Route.ProjectRoute.TaskDetail> { key ->
        TaskDetailScreenRoot(
            taskId = key.taskId,
            navigateBack = {
                backStack.remove(key)
            },
            onEditTextClick = { isEditMode, projectId, taskId ->
                backStack.add(
                    Route.ProjectRoute.EditTextNavKey(
                        isEditMode = isEditMode,
                        projectId = projectId,
                        target = EditTextTarget.TASK,
                        taskId = taskId,
                        subTaskId = null,
                    ),
                )
            },
        )
    }

    entry<Route.ProjectRoute.EditTextNavKey> { key ->
        val editTextVm: EditTextViewModel =
            koinViewModel {
                parametersOf(key.isEditMode, key.projectId, key.target, key.taskId, key.subTaskId)
            }
        EditTextScreenRoot(
            onNavigateBack = {
                backStack.remove(key)
            },
            viewModel = editTextVm,
        )
    }
}
