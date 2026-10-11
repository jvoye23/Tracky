package com.jvcs.tracky.features.onboarding.presentation.paywall

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import assertk.assertThat
import assertk.assertions.containsExactly
import com.jvcs.tracky.core.domain.purchases.ProPackage
import com.jvcs.tracky.core.domain.purchases.ProPackageType
import com.jvcs.tracky.core.domain.util.DataError
import com.jvcs.tracky.designsystem.theme.TrackyTheme
import com.jvcs.tracky.features.project.presentation.util.toUiText
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import tracky.composeapp.generated.resources.Res
import tracky.composeapp.generated.resources.close
import tracky.composeapp.generated.resources.done
import tracky.composeapp.generated.resources.error_no_internet
import tracky.composeapp.generated.resources.paywall_continue_free
import tracky.composeapp.generated.resources.paywall_no_plans
import tracky.composeapp.generated.resources.paywall_subscribe_now
import tracky.composeapp.generated.resources.plan_annual
import tracky.composeapp.generated.resources.plan_annual_description
import tracky.composeapp.generated.resources.plan_lifetime
import tracky.composeapp.generated.resources.plan_monthly
import tracky.composeapp.generated.resources.pro_active_continue
import tracky.composeapp.generated.resources.pro_active_title
import tracky.composeapp.generated.resources.restore_purchases
import tracky.composeapp.generated.resources.retry
import kotlin.test.Test

/** What the paywall shows for each state, and the action every control raises. */
@OptIn(ExperimentalTestApi::class)
internal class PaywallScreenTest {

    private fun text(resource: StringResource) = runBlocking { getString(resource) }

    private val monthly = ProPackage("monthly", ProPackageType.MONTHLY, "Pro Monthly", "€3.99")
    private val annual = ProPackage("annual", ProPackageType.ANNUAL, "Pro Yearly", "€29.99")
    private val lifetime = ProPackage("lifetime", ProPackageType.LIFETIME, "Pro Lifetime", "€79.99")
    private val promo = ProPackage("promo", ProPackageType.OTHER, "Store promo", "€1.99")

    private val plans =
        PaywallState(isLoading = false, plans = listOf(monthly, annual, lifetime), selectedPlanId = "annual")

    private fun ComposeUiTest.show(state: PaywallState, actions: MutableList<PaywallAction> = mutableListOf()) =
        setContent { TrackyTheme { PaywallScreen(state = state, onAction = { actions += it }) } }

    @Test
    fun thePlansShowTrackysNamesTheirPricesAndWhatTheyBill() =
        runComposeUiTest {
            show(plans)

            onNodeWithText(text(Res.string.plan_monthly)).assertExists()
            onNodeWithText(text(Res.string.plan_annual)).assertExists()
            onNodeWithText(text(Res.string.plan_lifetime)).assertExists()
            onNodeWithText(text(Res.string.plan_annual_description)).assertExists()
            onNodeWithText("€29.99").assertExists()
        }

    @Test
    fun aPlanTrackyHasNoCopyForShowsTheStoresTitle() =
        runComposeUiTest {
            show(plans.copy(plans = listOf(promo), selectedPlanId = "promo"))

            onNodeWithText("Store promo").assertExists()
            onNodeWithText("€1.99").assertExists()
        }

    @Test
    fun everyControlRaisesItsAction() =
        runComposeUiTest {
            val actions = mutableListOf<PaywallAction>()
            show(plans, actions)

            onNodeWithText(text(Res.string.plan_monthly)).performClick()
            onNodeWithText(text(Res.string.paywall_subscribe_now)).performClick()
            onNodeWithText(text(Res.string.restore_purchases)).performClick()
            onNodeWithText(text(Res.string.paywall_continue_free)).performClick()
            onNodeWithContentDescription(text(Res.string.close)).performClick()

            assertThat(actions).containsExactly(
                PaywallAction.OnPlanClick("monthly"),
                PaywallAction.OnSubscribeClick,
                PaywallAction.OnRestoreClick,
                PaywallAction.OnContinueFreeClick,
                PaywallAction.OnContinueFreeClick,
            )
        }

    @Test
    fun withoutASelectedPlanThereIsNothingToSubscribeTo() =
        runComposeUiTest {
            show(plans.copy(selectedPlanId = null))

            onNodeWithText(text(Res.string.paywall_subscribe_now)).assertIsNotEnabled()
            onNodeWithText(text(Res.string.paywall_continue_free)).assertIsEnabled()
        }

    @Test
    fun whileARestoreRunsNothingElseCanStart() =
        runComposeUiTest {
            show(plans.copy(isRestoring = true))

            onNodeWithText(text(Res.string.paywall_subscribe_now)).assertIsNotEnabled()
            onNodeWithText(text(Res.string.paywall_continue_free)).assertIsNotEnabled()
            onNodeWithContentDescription(text(Res.string.close)).assertIsNotEnabled()
            // The restore button shows its progress in place of the label.
            onNodeWithText(text(Res.string.restore_purchases)).assertDoesNotExist()
        }

    @Test
    fun aFailedLoadSaysWhyAndOffersARetry() =
        runComposeUiTest {
            val actions = mutableListOf<PaywallAction>()
            show(
                PaywallState(isLoading = false, loadError = DataError.Remote.NO_INTERNET.toUiText()),
                actions,
            )

            onNodeWithText(text(Res.string.error_no_internet)).assertExists()
            onNodeWithText(text(Res.string.retry)).performClick()

            assertThat(actions).containsExactly(PaywallAction.OnRetryClick)
        }

    @Test
    fun anEmptyOfferingSaysSoAndOffersARetry() =
        runComposeUiTest {
            val actions = mutableListOf<PaywallAction>()
            show(PaywallState(isLoading = false), actions)

            onNodeWithText(text(Res.string.paywall_no_plans)).assertExists()
            onNodeWithText(text(Res.string.retry)).performClick()

            assertThat(actions).containsExactly(PaywallAction.OnRetryClick)
        }

    @Test
    fun whileLoadingNoPlanIsShown() =
        runComposeUiTest {
            show(PaywallState())

            onNodeWithText(text(Res.string.plan_annual)).assertDoesNotExist()
            onNodeWithText(text(Res.string.retry)).assertDoesNotExist()
        }

    @Test
    fun alreadyProWithoutAnAccountGoesOnToCreateOne() =
        runComposeUiTest {
            val actions = mutableListOf<PaywallAction>()
            show(plans.copy(hasProAccess = true), actions)

            onNodeWithText(text(Res.string.pro_active_title)).assertExists()
            onNodeWithText(text(Res.string.paywall_subscribe_now)).assertDoesNotExist()
            onNodeWithText(text(Res.string.pro_active_continue)).performClick()

            assertThat(actions).containsExactly(PaywallAction.OnContinueProClick)
        }

    @Test
    fun alreadyProAndSignedInOnlyCloses() =
        runComposeUiTest {
            val actions = mutableListOf<PaywallAction>()
            show(plans.copy(hasProAccess = true, hasAccount = true), actions)

            onNodeWithText(text(Res.string.pro_active_continue)).assertDoesNotExist()
            onNodeWithText(text(Res.string.done)).performClick()

            assertThat(actions).containsExactly(PaywallAction.OnContinueProClick)
        }
}
