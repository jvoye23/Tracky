package com.jvcs.tracky.features.auth.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.Icon_Lock
import com.jvcs.tracky.design_system.Icon_Mail
import com.jvcs.tracky.design_system.components.AppleSignInButton
import com.jvcs.tracky.design_system.components.DividerWithLabel
import com.jvcs.tracky.design_system.components.GoogleSignInButton
import com.jvcs.tracky.design_system.components.TrackyPrimaryButton
import com.jvcs.tracky.design_system.components.TrackyTextField
import com.jvcs.tracky.design_system.components.Wordmark
import com.jvcs.tracky.design_system.components.WordmarkSize
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.continue_with_apple
import tracky.composeapp.generated.resources.continue_with_google
import tracky.composeapp.generated.resources.dont_have_account
import tracky.composeapp.generated.resources.email
import tracky.composeapp.generated.resources.forgot_password
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.login_subtitle
import tracky.composeapp.generated.resources.or_continue_with_email
import tracky.composeapp.generated.resources.password
import tracky.composeapp.generated.resources.sign_up
import tracky.composeapp.generated.resources.welcome_title
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.features.auth.presentation.register.RegisterScreen
import com.jvcs.tracky.features.auth.presentation.register.RegisterState
import com.jvcs.tracky.design_system.theme.authElevatedLabelStyle
import com.jvcs.tracky.design_system.theme.authLabelStyle
import com.jvcs.tracky.design_system.theme.authTextStyle
import tracky.composeapp.generated.resources.or_continue_with

@Composable
fun LoginScreenRoot(
    viewModel: LoginViewModel = koinViewModel(),
    onLoginSuccess: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onCreateAccountClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            LoginEvent.Success -> onLoginSuccess()
        }
    }

    LoginScreen(
        state = state,
        onAction = { action ->
            when (action) {
                LoginAction.OnForgotPasswordClick -> onForgotPasswordClick()
                LoginAction.OnSignUpClick -> onCreateAccountClick()
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@Composable
fun LoginScreen(
    state: LoginState,
    onAction: (LoginAction) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            Wordmark(size = WordmarkSize.Lg)

            Spacer(Modifier.height(36.dp))

            Text(
                text = stringResource(Res.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.login_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackyTextField(
                    state = state.emailTextFieldState,
                    label = stringResource(Res.string.email),
                    leadingIcon = Icon_Mail,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    error = state.emailError?.asString(),
                    modifier = Modifier.testTag("login_email"),
                    labelStyle = MaterialTheme.typography.authLabelStyle,
                    elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
                    textStyle = MaterialTheme.typography.authTextStyle,
                )
                TrackyTextField(
                    state = state.passwordTextFieldState,
                    label = stringResource(Res.string.password),
                    leadingIcon = Icon_Lock,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (state.canLogin) onAction(LoginAction.OnLoginClick)
                    },
                    error = state.passwordError?.asString(),
                    modifier = Modifier.testTag("login_password"),
                    labelStyle = MaterialTheme.typography.authLabelStyle,
                    elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
                    textStyle = MaterialTheme.typography.authTextStyle,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { onAction(LoginAction.OnForgotPasswordClick) },
                    modifier = Modifier.testTag("login_forgot_password"),
                ) {
                    Text(
                        text = stringResource(Res.string.forgot_password),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.error.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))

            TrackyPrimaryButton(
                text = stringResource(Res.string.login),
                onClick = { onAction(LoginAction.OnLoginClick) },
                enabled = state.canLogin,
                isLoading = state.isLoggingIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("login_button"),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.dont_have_account),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onAction(LoginAction.OnSignUpClick) }) {
                    Text(
                        text = stringResource(Res.string.sign_up),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            DividerWithLabel(label = stringResource(Res.string.or_continue_with))

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GoogleSignInButton(
                    text = stringResource(Res.string.continue_with_google),
                    onClick = { onAction(LoginAction.OnGoogleSignInClick) },
                    enabled = !state.isLoggingIn,
                )
                AppleSignInButton(
                    text = stringResource(Res.string.continue_with_apple),
                    onClick = { onAction(LoginAction.OnAppleSignInClick) },
                    enabled = !state.isLoggingIn,
                )
            }
        }
    }
}

@Preview (showSystemUi = false, device = Devices.PIXEL_9_PRO)
@Composable
private fun RegisterScreenPreview() {
    TrackyTheme {
        LoginScreen(
            state = LoginState(),
            onAction = {}
        )
    }
}
