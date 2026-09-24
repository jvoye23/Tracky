package com.jvcs.tracky.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
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

@Composable
fun NavigationRoot(backStack: NavBackStack<NavKey>, modifier: Modifier = Modifier) {
    NavDisplay(
        modifier = modifier.fillMaxSize(),
        backStack = backStack,
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                authAccountEntries(backStack)
                authEmailEntries(backStack)
                projectListEntries(backStack)
                projectDetailEntries(backStack)
                projectDetailChildEntries(backStack)
            },
    )
}
