package com.jvcs.tracky.design_system.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvcs.tracky.design_system.Icon_Eye
import com.jvcs.tracky.design_system.Icon_EyeOff
import com.jvcs.tracky.design_system.Icon_Lock
import com.jvcs.tracky.design_system.Icon_Mail
import com.jvcs.tracky.design_system.Icon_User
import com.jvcs.tracky.design_system.theme.TrackyTheme
import com.jvcs.tracky.design_system.theme.authElevatedLabelStyle
import com.jvcs.tracky.design_system.theme.authLabelStyle
import com.jvcs.tracky.design_system.theme.authTextStyle
import com.jvcs.tracky.design_system.theme.projectElevatedLabelStyle
import com.jvcs.tracky.design_system.theme.projectLabelStyle

private val LabelToTextSpacing = 4.dp

@Composable
fun TrackyTextField(
    state: TextFieldState,
    label: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isPassword: Boolean = false,
    error: String? = null,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onImeAction: (() -> Unit)? = null,
    labelStyle: TextStyle = TextStyle.Default,
    elevatedLabelStyle: TextStyle = TextStyle.Default,
    textStyle: TextStyle = TextStyle.Default,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    enabled: Boolean = true,
    showLabel: Boolean = true,
    borderDefaultColor: Color = MaterialTheme.colorScheme.outlineVariant,
    borderErrorColor: Color = MaterialTheme.colorScheme.error,
    borderIsFocusedColor: Color = MaterialTheme.colorScheme.primary,
    backgroundDefaultColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    backgroundErrorColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.33f ),
    labelDefaultColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    labelErrorColor: Color = MaterialTheme.colorScheme.error,
    labelIsFocusedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant

) {
    val keyboardActionHandler: KeyboardActionHandler? = onImeAction?.let { handler ->
        KeyboardActionHandler { _ -> handler() }
    }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    var hasText by remember { mutableStateOf(state.text.isNotEmpty()) }
    LaunchedEffect(state) {
        snapshotFlow { state.text.length }.collect { hasText = it > 0 }
    }

    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val isError = error != null
    val elevated = isFocused || hasText

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> borderErrorColor
            isFocused -> borderIsFocusedColor
            else -> borderDefaultColor
        }
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isError -> backgroundErrorColor
            else -> backgroundDefaultColor
        }
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isError -> labelErrorColor
            isFocused -> labelIsFocusedColor
            else -> labelDefaultColor
        }
    )

    val resolvedTextStyle = if (textStyle.color.isSpecified) {
        textStyle
    } else {
        textStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    }

    val selectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(backgroundColor)
                .border(
                    width = 1.5.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                ) {
                    if (elevated && showLabel) {
                        Text(
                            text = label,
                            color = labelColor,
                            style = elevatedLabelStyle,
                        )
                        Spacer(Modifier.height(LabelToTextSpacing))
                    }

                    Box(contentAlignment = Alignment.CenterStart) {
                        if (!elevated && showLabel) {
                            Text(
                                text = label,
                                color = labelColor,
                                style = labelStyle,
                            )
                        }

                        CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                            val fieldModifier = Modifier.fillMaxWidth()
                            if (isPassword) {
                                BasicSecureTextField(
                                    state = state,
                                    modifier = fieldModifier,
                                    enabled = enabled,
                                    textStyle = resolvedTextStyle,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    interactionSource = interactionSource,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = imeAction,
                                    ),
                                    onKeyboardAction = keyboardActionHandler,
                                    textObfuscationMode = if (passwordVisible) {
                                        TextObfuscationMode.Visible
                                    } else {
                                        TextObfuscationMode.RevealLastTyped
                                    },
                                )
                            } else {
                                BasicTextField(
                                    state = state,
                                    modifier = fieldModifier,
                                    enabled = enabled,
                                    textStyle = resolvedTextStyle,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    lineLimits = lineLimits,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = keyboardType,
                                        imeAction = imeAction,
                                        capitalization = capitalization,
                                    ),
                                    interactionSource = interactionSource,
                                    onKeyboardAction = keyboardActionHandler,
                                )
                            }
                        }
                    }
                }

                if (isPassword) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { passwordVisible = !passwordVisible },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icon_EyeOff else Icon_Eye,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (error != null || hint != null) {
            Text(
                text = error ?: hint.orEmpty(),
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isError) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier.padding(start = 20.dp),
            )
        }
    }
}

@Composable
private fun TrackyTextFieldPreviewContainer(content: @Composable ColumnScope.() -> Unit) {
    TrackyTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldEmptyPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldFilledPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Email",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldLeadingIconPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Email",
            leadingIcon = Icon_Mail,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Email",
            leadingIcon = Icon_Mail,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldHintPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Full name",
            leadingIcon = Icon_User,
            hint = "At least 3 characters",
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("Jane Doe"),
            label = "Full name",
            leadingIcon = Icon_User,
            hint = "At least 3 characters",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldErrorPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Email",
            leadingIcon = Icon_Mail,
            error = "Email is required",
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@"),
            label = "Email",
            leadingIcon = Icon_Mail,
            error = "That doesn't look like a valid email address",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldPasswordEmptyPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldPasswordFilledPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("sup3r-s3cret"),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            hint = "At least 9 characters, one number and one symbol",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldPasswordErrorPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("short"),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            error = "Password must be at least 9 characters long",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldDisabledPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Email",
            leadingIcon = Icon_Mail,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("sup3r-s3cret"),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldLongContentPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(
                "A really long single-line value that runs well past the right edge of the field",
            ),
            label = "Work email address used for account recovery",
            leadingIcon = Icon_Mail,
            error = "This address is already registered to another Tracky account, " +
                "try signing in instead or use a different address",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldMultiLinePreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(
                "Rebuild the onboarding flow so new users land on the project list " +
                    "instead of the empty timer screen.",
            ),
            label = "Description",
            lineLimits = TextFieldLineLimits.MultiLine(1, 4),
            labelStyle = MaterialTheme.typography.projectLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldAuthStylesPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("Jane Doe"),
            label = "Full name",
            leadingIcon = Icon_User,
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Email",
            leadingIcon = Icon_Mail,
            keyboardType = KeyboardType.Email,
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("sup3r-s3cret"),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            hint = "At least 9 characters, one number and one symbol",
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Confirm password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            imeAction = ImeAction.Done,
            error = "Passwords don't match",
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewLightDark
@Composable
private fun TrackyTextFieldProjectStylesPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState(),
            label = "Title",
            imeAction = ImeAction.Done,
            labelStyle = MaterialTheme.typography.projectLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
            textStyle = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("Tracky redesign"),
            label = "Title",
            imeAction = ImeAction.Done,
            labelStyle = MaterialTheme.typography.projectLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
            textStyle = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Font scale 2x", fontScale = 2f, widthDp = 360)
@Composable
private fun TrackyTextFieldFontScalePreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Email",
            leadingIcon = Icon_Mail,
            hint = "We'll send a confirmation link",
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("sup3r-s3cret"),
            label = "Password",
            leadingIcon = Icon_Lock,
            isPassword = true,
            error = "Password must be at least 9 characters long",
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(name = "Compact 320dp", widthDp = 320)
@Composable
private fun TrackyTextFieldCompactPreview() {
    TrackyTextFieldPreviewContainer {
        TrackyTextField(
            state = rememberTextFieldState("jane.doe@tracky.app"),
            label = "Work email address used for account recovery",
            leadingIcon = Icon_Mail,
            error = "This address is already registered to another Tracky account",
            labelStyle = MaterialTheme.typography.authLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.authElevatedLabelStyle,
            textStyle = MaterialTheme.typography.authTextStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        TrackyTextField(
            state = rememberTextFieldState("Tracky redesign"),
            label = "Title",
            labelStyle = MaterialTheme.typography.projectLabelStyle,
            elevatedLabelStyle = MaterialTheme.typography.projectElevatedLabelStyle,
            textStyle = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
