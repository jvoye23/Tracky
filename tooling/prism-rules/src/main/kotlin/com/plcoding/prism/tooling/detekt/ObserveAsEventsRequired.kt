package com.plcoding.prism.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * Reports a `LaunchedEffect` that collects a flow.
 *
 * One-time events go through the project's `ObserveAsEvents` helper, which ties
 * collection to the lifecycle and drops nothing on the way. A bare
 * `LaunchedEffect` + `collect` keeps collecting while the screen is in the
 * background and delivers events no one is there to see.
 */
class ObserveAsEventsRequired(config: Config) :
    Rule(
        config,
        "One-time events are observed through ObserveAsEvents, not a LaunchedEffect that collects.",
    ) {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != LAUNCHED_EFFECT) return
        // The implementation of ObserveAsEvents is itself a collecting LaunchedEffect.
        if (expression.getStrictParentOfType<KtNamedFunction>()?.name == OBSERVE_AS_EVENTS) return

        // snapshotFlow turns local Compose state (a pager, a scroll position) into a flow; those
        // are not one-time events from a ViewModel, and ObserveAsEvents is not for them.
        val collects =
            expression.lambdaArguments
                .flatMap { it.collectDescendantsOfType<KtDotQualifiedExpression>() }
                .any { it.selectorExpression?.let(::isCollectCall) == true && !it.isSnapshotFlowCollection() }
        if (!collects) return

        report(
            Finding(
                Entity.from(expression),
                "Collect this through ObserveAsEvents so collection " +
                    "follows the lifecycle.",
            ),
        )
    }

    private fun KtDotQualifiedExpression.isSnapshotFlowCollection(): Boolean {
        var receiver: KtExpression = receiverExpression
        while (receiver is KtDotQualifiedExpression) receiver = receiver.receiverExpression
        return (receiver as? KtCallExpression)?.calleeExpression?.text == SNAPSHOT_FLOW
    }

    private fun isCollectCall(selector: KtExpression): Boolean =
        (selector as? KtCallExpression)?.calleeExpression?.text in COLLECT_FUNCTIONS

    private companion object {
        const val LAUNCHED_EFFECT = "LaunchedEffect"
        const val OBSERVE_AS_EVENTS = "ObserveAsEvents"
        const val SNAPSHOT_FLOW = "snapshotFlow"
        val COLLECT_FUNCTIONS = setOf("collect", "collectLatest")
    }
}
