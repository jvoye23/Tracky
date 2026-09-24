package com.plcoding.prism.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports an `ObserveAsEvents` call outside a Root composable.
 *
 * One-time events are a screen-level concern: the Root owns the ViewModel and
 * therefore the event stream. A child observing events couples it to the
 * ViewModel it is not supposed to know about. The activity's `setContent`
 * block sits outside any composable function and is out of scope — the app
 * shell owns the app-level event stream there.
 */
class ObserveAsEventsOnlyInRoot(config: Config) :
    Rule(
        config,
        "ObserveAsEvents is called only inside Root composables.",
    ) {
    @Configuration("name suffixes that mark a composable as a Root, the owner of a ViewModel")
    private val rootSuffixes: List<String> by config(listOf(ROOT))

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeExpression?.text != OBSERVE_AS_EVENTS) return

        val enclosingFunctions = expression.parents.filterIsInstance<KtNamedFunction>().toList()
        if (enclosingFunctions.none { enclosing -> enclosing.isComposable() }) return
        if (enclosingFunctions.any { enclosing -> rootSuffixes.any { enclosing.name?.endsWith(it) == true } }) return

        report(
            Finding(
                Entity.from(expression),
                "Move this ObserveAsEvents call into the Root composable, which owns the " +
                    "ViewModel and its event stream.",
            ),
        )
    }

    private companion object {
        const val OBSERVE_AS_EVENTS = "ObserveAsEvents"
        const val ROOT = "Root"
    }
}
