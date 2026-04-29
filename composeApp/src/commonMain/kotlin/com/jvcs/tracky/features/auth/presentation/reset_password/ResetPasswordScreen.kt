package com.jvcs.tracky.features.auth.presentation.reset_password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.Icon_CheckCircle
import com.jvcs.tracky.design_system.Icon_Lock
import com.jvcs.tracky.design_system.components.AuthHeaderIcon
import com.jvcs.tracky.design_system.components.TrackyPrimaryButton
import com.jvcs.tracky.design_system.components.TrackyTextField
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.password
import tracky.composeapp.generated.resources.password_hint
import tracky.composeapp.generated.resources.reset_password_successfully
import tracky.composeapp.generated.resources.set_new_password
import tracky.composeapp.generated.resources.submit

@Composable
fun ResetPasswordScreenRoot(
    viewModel: ResetPasswordViewModel = koinViewModel(),
    onLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ResetPasswordScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ResetPasswordAction.OnLoginClick -> onLoginClick()
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@Composable
fun ResetPasswordScreen(
    state: ResetPasswordState,
    onAction: (ResetPasswordAction) -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.isResetSuccessful) {
                AuthHeaderIcon(
                    icon = Icon_CheckCircle,
                    containerSize = 88.dp,
                    iconSize = 44.dp,
                    cornerRadius = 28.dp,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(Res.string.reset_password_successfully),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                TrackyPrimaryButton(
                    text = stringResource(Res.string.login),
                    onClick = { onAction(ResetPasswordAction.OnLoginClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AuthHeaderIcon(
                    icon = Icon_Lock,
                    containerSize = 72.dp,
                    iconSize = 32.dp,
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(Res.string.set_new_password),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                TrackyTextField(
                    state = state.passwordTextState,
                    label = stringResource(Res.string.password),
                    leadingIcon = Icon_Lock,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (state.canSubmit) onAction(ResetPasswordAction.OnSubmitClick)
                    },
                    hint = stringResource(Res.string.password_hint),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (state.errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.errorText.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(24.dp))

                TrackyPrimaryButton(
                    text = stringResource(Res.string.submit),
                    onClick = { onAction(ResetPasswordAction.OnSubmitClick) },
                    enabled = state.canSubmit,
                    isLoading = state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
