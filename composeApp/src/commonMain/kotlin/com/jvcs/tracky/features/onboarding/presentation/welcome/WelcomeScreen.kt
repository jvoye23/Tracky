package com.jvcs.tracky.features.onboarding.presentation.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.jvcs.tracky.designsystem.components.TrackyPrimaryButton
import com.jvcs.tracky.designsystem.components.Wordmark
import com.jvcs.tracky.designsystem.components.WordmarkSize
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.onboarding.presentation.components.FeatureCard
import com.jvcs.tracky.features.onboarding.presentation.model.basicFeatures
import com.jvcs.tracky.features.onboarding.presentation.model.listOfColors
import org.jetbrains.compose.resources.stringResource
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.already_have_account
import tracky.composeapp.generated.resources.get_started
import tracky.composeapp.generated.resources.welcome_hero_subtitle
import tracky.composeapp.generated.resources.welcome_hero_title

/**
 * The first screen of onboarding: a hero headline and what the free version already does.
 *
 * No ViewModel - it holds no state and makes no decisions, so it only reports the two taps.
 */
@Composable
fun WelcomeScreenRoot(onGetStartedClick: () -> Unit, onLoginClick: () -> Unit) {
    WelcomeScreen(onGetStartedClick = onGetStartedClick, onLoginClick = onLoginClick)
}

@Composable
fun WelcomeScreen(
    onGetStartedClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            WelcomeActions(
                onGetStartedClick = onGetStartedClick,
                onLoginClick = onLoginClick,
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                // Capped so the cards stay readable lines on a tablet instead of stretching edge to edge.
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { WelcomeHero(modifier = Modifier.padding(bottom = 12.dp)) }
                itemsIndexed(basicFeatures) { index, feature ->
                    FeatureCard(
                        icon = feature.icon,
                        title = stringResource(feature.title),
                        description = stringResource(feature.description),
                        accentColor = listOfColors[index % listOfColors.size],
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark(size = WordmarkSize.Md)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.welcome_hero_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.welcome_hero_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WelcomeActions(
    onGetStartedClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        // A bottom bar draws under the navigation bar unless it pads for it itself.
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TrackyPrimaryButton(
                    text = stringResource(Res.string.get_started),
                    onClick = onGetStartedClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onLoginClick, modifier = Modifier.testTag("welcome_login")) {
                    Text(stringResource(Res.string.already_have_account))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun WelcomeScreenPreview() {
    TrackyTheme {
        WelcomeScreen(onGetStartedClick = {}, onLoginClick = {})
    }
}

@Preview(name = "Tablet", widthDp = 840)
@Composable
private fun WelcomeScreenTabletPreview() {
    TrackyTheme {
        WelcomeScreen(onGetStartedClick = {}, onLoginClick = {})
    }
}

@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun WelcomeScreenLargeFontPreview() {
    TrackyTheme {
        WelcomeScreen(onGetStartedClick = {}, onLoginClick = {})
    }
}
