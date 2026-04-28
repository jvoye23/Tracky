package com.jvcs.tracky.features.auth.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.design_system.util.ObserveAsEvents
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.already_have_account
import tracky.composeapp.generated.resources.create_account
import tracky.composeapp.generated.resources.email
import tracky.composeapp.generated.resources.password
import tracky.composeapp.generated.resources.password_hint
import tracky.composeapp.generated.resources.register
import tracky.composeapp.generated.resources.username
import tracky.composeapp.generated.resources.username_hint

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
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.create_account),
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = state.emailTextState.text.toString(),
                onValueChange = { newValue ->
                    state.emailTextState.edit {
                        replace(0, length, newValue)
                    }
                },
                label = { Text(stringResource(Res.string.email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                isError = state.emailError != null,
                supportingText = state.emailError?.let { error -> { Text(error.asString()) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.usernameTextState.text.toString(),
                onValueChange = { newValue ->
                    state.usernameTextState.edit {
                        replace(0, length, newValue)
                    }
                },
                label = { Text(stringResource(Res.string.username)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                isError = state.usernameError != null,
                supportingText = state.usernameError?.let { error ->
                    { Text(error.asString()) }
                } ?: { Text(stringResource(Res.string.username_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.passwordTextState.text.toString(),
                onValueChange = { newValue ->
                    state.passwordTextState.edit {
                        replace(0, length, newValue)
                    }
                },
                label = { Text(stringResource(Res.string.password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { onAction(RegisterAction.OnTogglePasswordVisibilityClick) }) {
                        Icon(
                            imageVector = if (state.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                visualTransformation = if (state.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = state.passwordError != null,
                supportingText = state.passwordError?.let { error ->
                    { Text(error.asString()) }
                } ?: { Text(stringResource(Res.string.password_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.registrationError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.registrationError.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onAction(RegisterAction.OnRegisterClick) },
                enabled = state.canRegister,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isRegistering) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    Text(stringResource(Res.string.register))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { onAction(RegisterAction.OnLoginClick) }) {
                Text(stringResource(Res.string.already_have_account))
            }
        }
    }
}
