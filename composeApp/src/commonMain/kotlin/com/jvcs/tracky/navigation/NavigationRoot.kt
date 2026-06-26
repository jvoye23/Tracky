package com.jvcs.tracky.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.jvcs.tracky.MainState
import com.jvcs.tracky.features.auth.presentation.email_verification.EmailVerificationScreenRoot
import com.jvcs.tracky.features.auth.presentation.email_verification.EmailVerificationViewModel
import com.jvcs.tracky.features.auth.presentation.forgot_password.ForgotPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.login.LoginScreenRoot
import com.jvcs.tracky.features.auth.presentation.register.RegisterScreenRoot
import com.jvcs.tracky.features.auth.presentation.register_success.RegisterSuccessScreenRoot
import com.jvcs.tracky.features.auth.presentation.register_success.RegisterSuccessViewModel
import com.jvcs.tracky.features.auth.presentation.reset_password.ResetPasswordScreenRoot
import com.jvcs.tracky.features.auth.presentation.reset_password.ResetPasswordViewModel
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.EditTextScreenRoot
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailAction
import com.jvcs.tracky.features.project_archive.presentation.project_archive.ProjectArchiveScreenRoot
import com.jvcs.tracky.features.project_archive.presentation.project_archive_detail.ProjectArchiveDetailScreen
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailScreenRoot
import com.jvcs.tracky.features.project_tracker.presentation.project_detail.ProjectDetailViewModel
import com.jvcs.tracky.features.project_tracker.presentation.project_overview.ProjectOverviewScreenRoot
import com.jvcs.tracky.features.project_tracker.presentation.task_detail.TaskDetailScreenRoot
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NavigationRoot(
    mainState: MainState,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (mainState.isCheckingAuth) {
        Scaffold {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val startDestination: NavKey = if (mainState.isLoggedIn) {
        Route.ProjectRoute.ProjectOverview
    } else {
        Route.AuthRoute.Login
    }

    val backStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.AuthRoute.Login::class, Route.AuthRoute.Login.serializer())
                    subclass(Route.AuthRoute.Register::class, Route.AuthRoute.Register.serializer())
                    subclass(Route.AuthRoute.RegisterSuccess::class, Route.AuthRoute.RegisterSuccess.serializer())
                    subclass(Route.AuthRoute.EmailVerification::class, Route.AuthRoute.EmailVerification.serializer())
                    subclass(Route.AuthRoute.ForgotPassword::class, Route.AuthRoute.ForgotPassword.serializer())
                    subclass(Route.AuthRoute.ResetPassword::class, Route.AuthRoute.ResetPassword.serializer())
                    subclass(Route.ProjectRoute.ProjectOverview::class, Route.ProjectRoute.ProjectOverview.serializer())
                    subclass(Route.ProjectRoute.ProjectArchive::class, Route.ProjectRoute.ProjectArchive.serializer())
                    subclass(Route.ProjectRoute.ProjectArchiveDetail::class, Route.ProjectRoute.ProjectArchiveDetail.serializer())
                    subclass(Route.ProjectRoute.ProjectDetail::class, Route.ProjectRoute.ProjectDetail.serializer())
                    subclass(Route.ProjectRoute.TaskDetail::class, Route.ProjectRoute.TaskDetail.serializer())
                }
            }
        },
        startDestination
    )

    val editTextCallback = remember { mutableStateOf<((String) -> Unit)?>(null) }

    LaunchedEffect(mainState.isLoggedIn) {
        if (!mainState.isLoggedIn) {
            backStack.removeAll { true }
            backStack.add(Route.AuthRoute.Login)
        }
    }

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
                    username = mainState.username,
                    email = mainState.email,
                    onLogout = onLogout,
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
                    parametersOf(key.isEditMode, key.projectId, key.editedText, key.editedTextType)
                }
                ProjectDetailScreenRoot(
                    navigateBack = {
                        backStack.remove(key)
                    },
                    viewModel = detailVm,
                    onEditTextClick = { text, editTextType ->
                        editTextCallback.value = { newText ->
                            detailVm.onAction(
                                action = ProjectDetailAction.OnEditTextChanged(
                                    editTextType = editTextType,
                                    value = newText
                                )
                            )
                        }
                        backStack.add(
                            Route.ProjectRoute.EditTextNavKey(
                                editText = text,
                                editTextType = editTextType
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
                    editTextType = key.editTextType,
                    editText = key.editText,
                    onCancelClick = {
                        backStack.remove(key)
                    },
                    onSaveClick = { updatedText, _ ->
                        editTextCallback.value?.invoke(updatedText)
                        backStack.remove(key)
                    }
                )
            }
        }
    )
}
