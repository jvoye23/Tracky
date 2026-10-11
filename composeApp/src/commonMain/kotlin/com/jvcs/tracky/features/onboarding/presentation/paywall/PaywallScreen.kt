package com.jvcs.tracky.features.onboarding.presentation.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.jvcs.tracky.designsystem.components.TrackyOutlinedButton
import com.jvcs.tracky.designsystem.components.TrackyPrimaryButton
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.designsystem.util.ObserveAsEvents
import com.jvcs.tracky.designsystem.util.UiText
import com.jvcs.tracky.features.onboarding.presentation.components.FeatureCard
import com.jvcs.tracky.features.onboarding.presentation.components.PlanCard
import com.jvcs.tracky.features.onboarding.presentation.model.listOfColors
import com.jvcs.tracky.features.onboarding.presentation.model.proFeatures
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.already_have_account
import tracky.composeapp.generated.resources.already_have_subscription
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.done
import tracky.composeapp.generated.resources.error_no_internet
import tracky.composeapp.generated.resources.more_features_with_tracky_pro
import tracky.composeapp.generated.resources.ok
import tracky.composeapp.generated.resources.paywall_continue_free
import tracky.composeapp.generated.resources.paywall_no_plans
import tracky.composeapp.generated.resources.paywall_plan_unavailable
import tracky.composeapp.generated.resources.paywall_plans_title
import tracky.composeapp.generated.resources.paywall_purchase_failed_title
import tracky.composeapp.generated.resources.paywall_restore_title
import tracky.composeapp.generated.resources.paywall_subscribe_now
import tracky.composeapp.generated.resources.paywall_subtitle
import tracky.composeapp.generated.resources.paywall_title
import tracky.composeapp.generated.resources.paywall_title_brand
import tracky.composeapp.generated.resources.plan_annual
import tracky.composeapp.generated.resources.plan_annual_description
import tracky.composeapp.generated.resources.plan_lifetime
import tracky.composeapp.generated.resources.plan_lifetime_description
import tracky.composeapp.generated.resources.plan_monthly
import tracky.composeapp.generated.resources.plan_monthly_description
import tracky.composeapp.generated.resources.pro_active_continue
import tracky.composeapp.generated.resources.pro_active_description
import tracky.composeapp.generated.resources.pro_active_title
import tracky.composeapp.generated.resources.restore_nothing_found
import tracky.composeapp.generated.resources.restore_purchases
import tracky.composeapp.generated.resources.retry

@Composable
fun PaywallScreenRoot(
    source: PaywallSource,
    onContinueFree: () -> Unit,
    onCreateAccount: () -> Unit,
    onClose: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel { parametersOf(source) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var purchaseError by remember { mutableStateOf<UiText?>(null) }
    var restoreMessage by remember { mutableStateOf<UiText?>(null) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            PaywallEvent.NavigateToApp -> onContinueFree()
            PaywallEvent.NavigateToRegister -> onCreateAccount()
            PaywallEvent.ProActivated -> onClose()
            is PaywallEvent.ShowPurchaseError -> purchaseError = event.message
            is PaywallEvent.ShowRestoreMessage -> restoreMessage = event.message
        }
    }

    PaywallScreen(state = state, onAction = viewModel::onAction)

    purchaseError?.let { message ->
        PaywallMessageDialog(
            title = Res.string.paywall_purchase_failed_title,
            message = message,
            onDismiss = { purchaseError = null },
        )
    }
    restoreMessage?.let { message ->
        PaywallMessageDialog(
            title = Res.string.paywall_restore_title,
            message = message,
            onDismiss = { restoreMessage = null },
        )
    }
}

@Composable
private fun PaywallMessageDialog(
    title: StringResource,
    message: UiText,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(title)) },
        text = { Text(text = message.asString()) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.ok))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    state: PaywallState,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val payWallActionSectionHeight = remember { mutableIntStateOf(0) }

    if (state.hasProAccess) {
        ProActiveContent(
            hasAccount = state.hasAccount,
            onContinueClick = { onAction(PaywallAction.OnContinueProClick) },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    val brand = stringResource(Res.string.paywall_title_brand)
                    val title = stringResource(Res.string.paywall_title, brand)
                    val brandColor = MaterialTheme.colorScheme.primary
                    // Only the brand name is tinted; the rest of the line keeps the surface text colour.
                    val styledTitle =
                        remember(title, brand, brandColor) {
                            buildAnnotatedString {
                                append(title)
                                val start = title.indexOf(brand)
                                if (start >= 0) {
                                    addStyle(SpanStyle(color = brandColor), start, start + brand.length)
                                }
                            }
                        }
                    Text(
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        text = styledTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { onAction(PaywallAction.OnContinueFreeClick) },
                        enabled = !state.isBusy,
                    ) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(Res.string.close))
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                // Capped so the cards stay readable lines on a tablet instead of stretching edge to edge.
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp)
                        .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                plansSection(state = state, onAction = onAction)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,

                    ) {
                        TextButton(onClick = { onAction(PaywallAction.OnRestoreClick) }, enabled = !state.isBusy) {
                            if (state.isRestoring) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = stringResource(Res.string.restore_purchases),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        text = stringResource(Res.string.more_features_with_tracky_pro),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                itemsIndexed(proFeatures) { index, feature ->
                    FeatureCard(
                        icon = feature.icon,
                        title = stringResource(feature.title),
                        description = stringResource(feature.description),
                        accentColor = listOfColors[index % listOfColors.size],
                    )
                }
                item {
                    Spacer(modifier = Modifier.height((payWallActionSectionHeight.intValue / 2).dp))
                }
            }
            PaywallActions(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { payWallActionSectionHeight.intValue = it.height },
                canSubscribe = state.canSubscribe,
                isPurchasing = state.isPurchasing,
                isBusy = state.isBusy,
                onAction = onAction,
            )
        }
    }
}

private fun LazyListScope.plansSection(state: PaywallState, onAction: (PaywallAction) -> Unit) {
    item {
        Text(
            text = stringResource(Res.string.paywall_plans_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    when {
        state.isLoading -> {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.loadError != null -> {
            item {
                PlansMessage(
                    text = state.loadError.asString(),
                    onRetryClick = { onAction(PaywallAction.OnRetryClick) },
                )
            }
        }

        state.plans.isEmpty() -> {
            item {
                PlansMessage(
                    text = stringResource(Res.string.paywall_no_plans),
                    onRetryClick = { onAction(PaywallAction.OnRetryClick) },
                )
            }
        }

        else -> {
            items(state.plans, key = { it.identifier }) { plan ->
                PlanCard(
                    title = plan.label(),
                    description = plan.description(),
                    price = plan.formattedPrice,
                    isSelected = plan.identifier == state.selectedPlanId,
                    onClick = { onAction(PaywallAction.OnPlanClick(plan.identifier)) },
                    enabled = !state.isBusy,
                    isRecommended = plan.type == ProPackageType.ANNUAL,
                )
            }
        }
    }
}

@Composable
private fun PlansMessage(
    text: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetryClick) {
            Text(stringResource(Res.string.retry))
        }
    }
}

@Composable
private fun PaywallActions(
    canSubscribe: Boolean,
    isPurchasing: Boolean,
    isBusy: Boolean,
    onAction: (PaywallAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        modifier =
            modifier
                .fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackyPrimaryButton(
                    text = stringResource(Res.string.paywall_subscribe_now),
                    onClick = { onAction(PaywallAction.OnSubscribeClick) },
                    enabled = canSubscribe,
                    isLoading = isPurchasing,
                    modifier = Modifier.fillMaxWidth(),
                )
                TrackyOutlinedButton(
                    text = stringResource(Res.string.paywall_continue_free),
                    onClick = { onAction(PaywallAction.OnContinueFreeClick) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                )
            }
        }
    }
}

@Composable
private fun ProActiveContent(
    hasAccount: Boolean,
    onContinueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 28.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(Res.string.pro_active_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.pro_active_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                TrackyPrimaryButton(
                    // Signed in, Pro is already linked to the account; otherwise the account comes next.
                    text = stringResource(if (hasAccount) Res.string.done else Res.string.pro_active_continue),
                    onClick = onContinueClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The plan's name: Tracky's own label for the three known plans, the store's title otherwise. */
@Composable
private fun ProPackage.label(): String =
    when (type) {
        ProPackageType.MONTHLY -> stringResource(Res.string.plan_monthly)
        ProPackageType.ANNUAL -> stringResource(Res.string.plan_annual)
        ProPackageType.LIFETIME -> stringResource(Res.string.plan_lifetime)
        ProPackageType.OTHER -> title
    }

/** What the plan means for billing; nothing for a store package Tracky has no copy for. */
@Composable
private fun ProPackage.description(): String? =
    when (type) {
        ProPackageType.MONTHLY -> stringResource(Res.string.plan_monthly_description)
        ProPackageType.ANNUAL -> stringResource(Res.string.plan_annual_description)
        ProPackageType.LIFETIME -> stringResource(Res.string.plan_lifetime_description)
        ProPackageType.OTHER -> null
    }

@Suppress("ScreenStateOnlyInScreenComposable") // preview wrapper: hands the whole state to the Screen
@Composable
private fun ScreenPreview(state: PaywallState) {
    TrackyTheme {
        PaywallScreen(state = state, onAction = {})
    }
}

private val previewPlans =
    listOf(
        ProPackage("\$rc_monthly", ProPackageType.MONTHLY, "Tracky Pro Monthly", "€3.99"),
        ProPackage("\$rc_annual", ProPackageType.ANNUAL, "Tracky Pro Yearly", "€29.99"),
        ProPackage("\$rc_lifetime", ProPackageType.LIFETIME, "Tracky Pro Lifetime", "€79.99"),
    )

private val plansState = PaywallState(isLoading = false, plans = previewPlans, selectedPlanId = "\$rc_annual")

@PreviewLightDark
@Composable
private fun PaywallScreenLoadingPreview() = ScreenPreview(PaywallState())

@PreviewLightDark
@Composable
private fun PaywallScreenPlansPreview() = ScreenPreview(plansState)

@PreviewLightDark
@Composable
private fun PaywallScreenLoadErrorPreview() =
    ScreenPreview(PaywallState(isLoading = false, loadError = UiText.Resource(Res.string.error_no_internet)))

@PreviewLightDark
@Composable
private fun PaywallScreenNoPlansPreview() = ScreenPreview(PaywallState(isLoading = false))

@PreviewLightDark
@Composable
private fun PaywallScreenPurchasingPreview() = ScreenPreview(plansState.copy(isPurchasing = true))

@PreviewLightDark
@Composable
private fun PurchaseErrorDialogPreview() {
    TrackyTheme {
        PaywallMessageDialog(
            title = Res.string.paywall_purchase_failed_title,
            message = UiText.Resource(Res.string.paywall_plan_unavailable),
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun RestoreMessageDialogPreview() {
    TrackyTheme {
        PaywallMessageDialog(
            title = Res.string.paywall_restore_title,
            message = UiText.Resource(Res.string.restore_nothing_found),
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun PaywallScreenRestoringPreview() = ScreenPreview(plansState.copy(isRestoring = true))

@PreviewLightDark
@Composable
private fun PaywallScreenProActivePreview() = ScreenPreview(plansState.copy(hasProAccess = true))

@PreviewLightDark
@Composable
private fun PaywallScreenProActiveSignedInPreview() =
    ScreenPreview(plansState.copy(hasProAccess = true, hasAccount = true))

@Preview(name = "Tablet", widthDp = 840)
@Composable
private fun PaywallScreenTabletPreview() = ScreenPreview(plansState)

@Preview(name = "Large font", fontScale = 2f)
@Composable
private fun PaywallScreenLargeFontPreview() = ScreenPreview(plansState)
