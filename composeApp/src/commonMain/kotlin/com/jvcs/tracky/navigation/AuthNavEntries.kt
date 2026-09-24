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

/** Login, sign-up and the sign-up confirmation. */
fun EntryProviderScope<NavKey>.authAccountEntries(backStack: NavBackStack<NavKey>) {
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
            },
        )
    }

    entry<Route.AuthRoute.Register> {
        RegisterScreenRoot(
            onRegisterSuccess = { email ->
                backStack.add(Route.AuthRoute.RegisterSuccess(email))
            },
            onLoginClick = {
                backStack.removeLastOrNull()
            },
        )
    }

    entry<Route.AuthRoute.RegisterSuccess> { key ->
        val vm: RegisterSuccessViewModel =
            koinViewModel {
                parametersOf(key.email)
            }
        RegisterSuccessScreenRoot(
            viewModel = vm,
            onLoginClick = {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Login)
            },
        )
    }
}

/** Screens reached from an email link: verification and password reset. */
fun EntryProviderScope<NavKey>.authEmailEntries(backStack: NavBackStack<NavKey>) {
    entry<Route.AuthRoute.EmailVerification> { key ->
        val vm: EmailVerificationViewModel =
            koinViewModel {
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
            },
        )
    }

    entry<Route.AuthRoute.ForgotPassword> {
        ForgotPasswordScreenRoot(
            onBackClick = {
                backStack.removeLastOrNull()
            },
        )
    }

    entry<Route.AuthRoute.ResetPassword> { key ->
        val vm: ResetPasswordViewModel =
            koinViewModel {
                parametersOf(key.token)
            }
        ResetPasswordScreenRoot(
            viewModel = vm,
            onLoginClick = {
                backStack.removeAll { true }
                backStack.add(Route.AuthRoute.Login)
            },
        )
    }
}
