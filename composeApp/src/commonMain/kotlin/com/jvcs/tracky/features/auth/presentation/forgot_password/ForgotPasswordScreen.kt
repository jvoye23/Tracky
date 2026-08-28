package com.jvcs.tracky.features.auth.presentation.forgot_password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.Icon_ArrowLeft
import com.jvcs.tracky.design_system.Icon_CheckCircle
import com.jvcs.tracky.design_system.Icon_Lock
import com.jvcs.tracky.design_system.Icon_Mail
import com.jvcs.tracky.design_system.components.AuthHeaderIcon
import com.jvcs.tracky.design_system.components.TrackyPrimaryButton
import com.jvcs.tracky.design_system.components.TrackyTextField
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.back_to_login
import tracky.composeapp.generated.resources.check_your_email
import tracky.composeapp.generated.resources.check_your_email_desc
import tracky.composeapp.generated.resources.email
import tracky.composeapp.generated.resources.forgot_password_desc
import tracky.composeapp.generated.resources.forgot_password_title
import tracky.composeapp.generated.resources.resend_email
import tracky.composeapp.generated.resources.reset_password
import tracky.composeapp.generated.resources.send_reset_link
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreenRoot(
    viewModel: ForgotPasswordViewModel = koinViewModel(),
    onBackClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ForgotPasswordScreen(
        state = state,
        onAction = { action ->
            when (action) {
                ForgotPasswordAction.OnBackClick,
                ForgotPasswordAction.OnBackToLoginClick -> onBackClick()
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onAction: (ForgotPasswordAction) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.reset_password)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(ForgotPasswordAction.OnBackClick) }) {
                        Icon(
                            imageVector = Icon_ArrowLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            )
        }
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
            if (state.isEmailSentSuccessfully) {
                AuthHeaderIcon(
                    icon = Icon_CheckCircle,
                    containerSize = 88.dp,
                    iconSize = 44.dp,
                    cornerRadius = 28.dp,
                    modifier = Modifier.testTag("forgot_password_success"),
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = stringResource(Res.string.check_your_email),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))

                val descTemplate = stringResource(Res.string.check_your_email_desc)
                val email = state.emailTextFieldState.text.toString()
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                val primary = MaterialTheme.colorScheme.primary
                val annotated = buildAnnotatedString {
                    val placeholder = "%1\$s"
                    val parts = descTemplate.split(placeholder, limit = 2)
                    withStyle(SpanStyle(color = onSurfaceVariant)) {
                        append(parts.getOrElse(0) { "" })
                    }
                    withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Medium)) {
                        append(email)
                    }
                    if (parts.size > 1) {
                        withStyle(SpanStyle(color = onSurfaceVariant)) { append(parts[1]) }
                    }
                }

                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                TrackyPrimaryButton(
                    text = stringResource(Res.string.back_to_login),
                    onClick = { onAction(ForgotPasswordAction.OnBackToLoginClick) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                TextButton(onClick = { onAction(ForgotPasswordAction.OnResendClick) }) {
                    Text(
                        text = stringResource(Res.string.resend_email),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                AuthHeaderIcon(
                    icon = Icon_Lock,
                    containerSize = 72.dp,
                    iconSize = 32.dp,
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(Res.string.forgot_password_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(Res.string.forgot_password_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                TrackyTextField(
                    state = state.emailTextFieldState,
                    label = stringResource(Res.string.email),
                    leadingIcon = Icon_Mail,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (state.canSubmit) onAction(ForgotPasswordAction.OnSubmitClick)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_email"),
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

                Spacer(Modifier.height(20.dp))

                TrackyPrimaryButton(
                    text = stringResource(Res.string.send_reset_link),
                    onClick = { onAction(ForgotPasswordAction.OnSubmitClick) },
                    enabled = state.canSubmit,
                    isLoading = state.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_submit"),
                )

                Spacer(Modifier.height(16.dp))

                TextButton(onClick = { onAction(ForgotPasswordAction.OnBackToLoginClick) }) {
                    Text(
                        text = stringResource(Res.string.back_to_login),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
