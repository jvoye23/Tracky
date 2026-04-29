package com.jvcs.tracky.features.auth.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jvcs.tracky.design_system.Icon_Lock
import com.jvcs.tracky.design_system.Icon_Mail
import com.jvcs.tracky.design_system.Icon_User
import com.jvcs.tracky.design_system.components.AppleSignInButton
import com.jvcs.tracky.design_system.components.DividerWithLabel
import com.jvcs.tracky.design_system.components.GoogleSignInButton
import com.jvcs.tracky.design_system.components.TrackyCheckbox
import com.jvcs.tracky.design_system.components.TrackyPrimaryButton
import com.jvcs.tracky.design_system.components.TrackyTextField
import com.jvcs.tracky.design_system.components.Wordmark
import com.jvcs.tracky.design_system.components.WordmarkSize
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.already_have_account_prefix
import tracky.composeapp.generated.resources.and_word
import tracky.composeapp.generated.resources.apple
import tracky.composeapp.generated.resources.confirm_password
import tracky.composeapp.generated.resources.create_account
import tracky.composeapp.generated.resources.email
import tracky.composeapp.generated.resources.full_name
import tracky.composeapp.generated.resources.full_name_hint
import tracky.composeapp.generated.resources.google
import tracky.composeapp.generated.resources.i_agree_to_terms_prefix
import tracky.composeapp.generated.resources.login
import tracky.composeapp.generated.resources.or_sign_up_with_email
import tracky.composeapp.generated.resources.password
import tracky.composeapp.generated.resources.password_hint
import tracky.composeapp.generated.resources.privacy_policy
import tracky.composeapp.generated.resources.register_subtitle
import tracky.composeapp.generated.resources.terms_of_service

@Composable
fun RegisterScreenRoot(
    viewModel: RegisterViewModel = koinViewModel(),
    onRegisterSuccess: (email: String) -> Unit,
    onLoginClick: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RegisterEvent.Success -> onRegisterSuccess(event.email)
        }
    }

    RegisterScreen(
        state = state,
        onAction = { action ->
            when (action) {
                RegisterAction.OnLoginClick -> onLoginClick()
                else -> Unit
            }
            viewModel.onAction(action)
        }
    )
}

@Composable
fun RegisterScreen(
    state: RegisterState,
    onAction: (RegisterAction) -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(size = WordmarkSize.Md)

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(Res.string.create_account),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(Res.string.register_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GoogleSignInButton(
                    text = stringResource(Res.string.google),
                    onClick = { onAction(RegisterAction.OnGoogleSignInClick) },
                    enabled = !state.isRegistering,
                    modifier = Modifier.weight(1f),
                )
                AppleSignInButton(
                    text = stringResource(Res.string.apple),
                    onClick = { onAction(RegisterAction.OnAppleSignInClick) },
                    enabled = !state.isRegistering,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            DividerWithLabel(label = stringResource(Res.string.or_sign_up_with_email))

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackyTextField(
                    state = state.nameTextState,
                    label = stringResource(Res.string.full_name),
                    leadingIcon = Icon_User,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                    error = state.nameError?.asString(),
                    hint = if (state.nameError == null) stringResource(Res.string.full_name_hint) else null,
                )
                TrackyTextField(
                    state = state.emailTextState,
                    label = stringResource(Res.string.email),
                    leadingIcon = Icon_Mail,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                    error = state.emailError?.asString(),
                )
                TrackyTextField(
                    state = state.passwordTextState,
                    label = stringResource(Res.string.password),
                    leadingIcon = Icon_Lock,
                    isPassword = true,
                    imeAction = ImeAction.Next,
                    error = state.passwordError?.asString(),
                    hint = if (state.passwordError == null) stringResource(Res.string.password_hint) else null,
                )
                TrackyTextField(
                    state = state.confirmPasswordTextState,
                    label = stringResource(Res.string.confirm_password),
                    leadingIcon = Icon_Lock,
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (state.canRegister) onAction(RegisterAction.OnRegisterClick)
                    },
                    error = state.confirmPasswordError?.asString(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                TrackyCheckbox(
                    checked = state.agreedToTerms,
                    onCheckedChange = { onAction(RegisterAction.OnTermsToggle(it)) },
                    modifier = Modifier.padding(top = 1.dp),
                )
                val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                val primaryColor = MaterialTheme.colorScheme.primary
                val prefix = stringResource(Res.string.i_agree_to_terms_prefix)
                val tos = stringResource(Res.string.terms_of_service)
                val andWord = stringResource(Res.string.and_word)
                val privacy = stringResource(Res.string.privacy_policy)
                val termsAnnotated = buildAnnotatedString {
                    withStyle(SpanStyle(color = onSurfaceVariantColor)) { append(prefix) }
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                        append(tos)
                    }
                    withStyle(SpanStyle(color = onSurfaceVariantColor)) { append(andWord) }
                    withStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Medium)) {
                        append(privacy)
                    }
                }
                Text(
                    text = termsAnnotated,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.termsError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.termsError.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                )
            }

            if (state.registrationError != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.registrationError.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            TrackyPrimaryButton(
                text = stringResource(Res.string.create_account),
                onClick = { onAction(RegisterAction.OnRegisterClick) },
                enabled = state.canRegister,
                isLoading = state.isRegistering,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.already_have_account_prefix),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { onAction(RegisterAction.OnLoginClick) }) {
                    Text(
                        text = stringResource(Res.string.login),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
