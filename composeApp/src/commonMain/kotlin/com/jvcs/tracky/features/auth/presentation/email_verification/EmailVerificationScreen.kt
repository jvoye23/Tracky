package com.jvcs.tracky.features.auth.presentation.email_verification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.Icon_CheckCircle
import com.jvcs.tracky.design_system.Icon_ErrorCircle
import com.jvcs.tracky.design_system.components.AuthHeaderIcon
import com.jvcs.tracky.design_system.components.TrackyPrimaryButton
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.email_verified_failed
import tracky.composeapp.generated.resources.email_verified_failed_desc
import tracky.composeapp.generated.resources.email_verified_successfully
import tracky.composeapp.generated.resources.email_verified_successfully_desc
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.verifying_account

@Composable
fun EmailVerificationScreenRoot(
    viewModel: EmailVerificationViewModel = koinViewModel(),
    onLoginClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EmailVerificationScreen(
        state = state,
        onAction = { action ->
            when (action) {
                EmailVerificationAction.OnLoginClick -> onLoginClick()
                EmailVerificationAction.OnCloseClick -> onCloseClick()
            }
        }
    )
}

@Composable
fun EmailVerificationScreen(
    state: EmailVerificationState,
    onAction: (EmailVerificationAction) -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                state.isVerifying -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(Res.string.verifying_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.isVerified -> {
                    AuthHeaderIcon(
                        icon = Icon_CheckCircle,
                        containerSize = 88.dp,
                        iconSize = 44.dp,
                        cornerRadius = 28.dp,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(Res.string.email_verified_successfully),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(Res.string.email_verified_successfully_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    TrackyPrimaryButton(
                        text = stringResource(Res.string.login),
                        onClick = { onAction(EmailVerificationAction.OnLoginClick) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                state.hasFailed -> {
                    AuthHeaderIcon(
                        icon = Icon_ErrorCircle,
                        containerSize = 88.dp,
                        iconSize = 44.dp,
                        cornerRadius = 28.dp,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        iconTint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(Res.string.email_verified_failed),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(Res.string.email_verified_failed_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))
                    TrackyPrimaryButton(
                        text = stringResource(Res.string.close),
                        onClick = { onAction(EmailVerificationAction.OnCloseClick) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
