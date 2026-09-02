package com.jvcs.tracky.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jvcs.tracky.features.auth.presentation.email_verification.EmailVerificationScreenRoot
import com.jvcs.tracky.features.auth.presentation.email_verification.EmailVerificationViewModel
import com.jvcs.tracky.features.auth.presentation.forgot_password.ForgotPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.login.LoginScreenRoot
import com.jvcs.tracky.features.auth.presentation.register.RegisterScreenRoot
import com.jvcs.tracky.features.auth.presentation.register_success.RegisterSuccessScreenRoot
import com.jvcs.tracky.features.auth.presentation.register_success.RegisterSuccessViewModel
import com.jvcs.tracky.features.auth.presentation.reset_password.ResetPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.reset_password.ResetPasswordViewModel
import com.jvcs.tracky.features.project.presentation.project_detail.EditTextScreenRoot
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project.presentation.project_archive.ProjectArchiveScreenRoot
import com.jvcs.tracky.features.project.presentation.project_archive_detail.ProjectArchiveDetailScreen
import com.jvcs.tracky.features.project.presentation.project_trash.ProjectTrashScreenRoot
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailScreenRoot
import com.jvcs.tracky.features.project.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project.presentation.project_overview.ProjectOverviewScreenRoot
import com.jvcs.tracky.features.project.presentation.task_detail.TaskDetailScreenRoot
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NavigationRoot(
    backStack: NavBackStack<NavKey>
) {
    val editTextCallback = remember { mutableStateOf<((String, String) -> Unit)?>(null) }

    NavDisplay(
        modifier = Modifier.fillMaxSize(),
        backStack = backStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            // Auth screens
            entry<Route.AuthRoute.Login> {
                LoginScreenRoot(
                    onLoginSuccess = {
                        backStack.removeAll { true }
                        backStack.add(Route.ProjectRoute.ProjectOverview)
                    },
                    onForgotPasswordClick = {
                        backStack.add(Route.AuthRoute.ForgotPassword)
                    },
                    onCreateAccountClick = {
                        backStack.add(Route.AuthRoute.Register)
                    }
                )
            }
            entry<Route.AuthRoute.Register> {
                RegisterScreenRoot(
                    onRegisterSuccess = { email ->
                        backStack.add(Route.AuthRoute.RegisterSuccess(email))
                    },
                    onLoginClick = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<Route.AuthRoute.RegisterSuccess> { key ->
                val vm: RegisterSuccessViewModel = koinViewModel {
                    parametersOf(key.email)
                }
                RegisterSuccessScreenRoot(
                    viewModel = vm,
                    onLoginClick = {
                        backStack.removeAll { true }
                        backStack.add(Route.AuthRoute.Login)
                    }
                )
            }
            entry<Route.AuthRoute.EmailVerification> { key ->
                val vm: EmailVerificationViewModel = koinViewModel {
                    parametersOf(key.token)
                }
                EmailVerificationScreenRoot(
                    viewModel = vm,
                    onLoginClick = {
                        backStack.removeAll { true }
                        backStack.add(Route.AuthRoute.Login)
                    },
                    onCloseClick = {
                        backStack.removeAll { true }
                        backStack.add(Route.AuthRoute.Login)
                    }
                )
            }
            entry<Route.AuthRoute.ForgotPassword> {
                ForgotPasswordScreenRoot(
                    onBackClick = {
                        backStack.removeLastOrNull()
                    }
                )
            }
            entry<Route.AuthRoute.ResetPassword> { key ->
                val vm: ResetPasswordViewModel = koinViewModel {
                    parametersOf(key.token)
                }
                ResetPasswordScreenRoot(
                    viewModel = vm,
                    onLoginClick = {
                        backStack.removeAll { true }
                        backStack.add(Route.AuthRoute.Login)
                    }
                )
            }

            // Project screens
            entry<Route.ProjectRoute.ProjectOverview> {
                ProjectOverviewScreenRoot(
                    onNavigateToDetailScreen = { projectId ->
                        backStack.add(
                            Route.ProjectRoute.ProjectDetail(
                                isEditMode = false,
                                projectId = projectId
                            )
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
                    }
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
                    }
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
                    }
                )
            }
            entry<Route.ProjectRoute.ProjectArchiveDetail> { key ->
                ProjectArchiveDetailScreen(
                    projectId = key.projectId,
                    onNavigateBack = {
                        backStack.remove(key)
                    }
                )
            }
            entry<Route.ProjectRoute.ProjectDetail> { key ->
                val detailVm: ProjectDetailViewModel = koinViewModel {
                    parametersOf(key.isEditMode, key.projectId)
                }
                ProjectDetailScreenRoot(
                    navigateBack = {
                        backStack.remove(key)
                    },
                    viewModel = detailVm,
                    onEditTextClick = { isEditMode, title, description, colorArgb ->
                        editTextCallback.value = { newTitle, newDescription ->
                            detailVm.onAction(
                                action = ProjectDetailAction.OnEditTextChanged(
                                    title = newTitle,
                                    description = newDescription
                                )
                            )
                        }
                        backStack.add(
                            Route.ProjectRoute.EditTextNavKey(
                                isEditMode = isEditMode,
                                titleText = title,
                                descriptionText = description,
                                colorArgb = colorArgb
                            )
                        )
                    },
                    onProjectTaskClick = { sessionId ->
                        backStack.add(
                            Route.ProjectRoute.TaskDetail(sessionId)
                        )
                    }
                )
            }
            entry<Route.ProjectRoute.TaskDetail> { key ->
                TaskDetailScreenRoot(
                    taskId = key.taskId,
                    navigateBack = {
                        backStack.remove(key)
                    }
                )
            }
            entry<Route.ProjectRoute.EditTextNavKey> { key ->
                EditTextScreenRoot(
                    isEditMode = key.isEditMode,
                    titleText = key.titleText,
                    descriptionText = key.descriptionText,
                    projectColor = key.colorArgb?.let { Color(it) },
                    onCancelClick = {
                        backStack.remove(key)
                    },
                    onSaveClick = { updatedTitle, updatedDescription ->
                        editTextCallback.value?.invoke(updatedTitle, updatedDescription)
                        backStack.remove(key)
                    }
                )
            }
        }
    )
}
