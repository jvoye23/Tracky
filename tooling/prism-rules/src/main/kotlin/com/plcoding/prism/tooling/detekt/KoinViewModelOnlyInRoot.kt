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
 * Reports a `koinViewModel()` or `viewModel()` call outside a Root composable.
 *
 * The Root is the single place a screen obtains its ViewModel; everything
 * below it receives state and callbacks. A ViewModel resolved deeper in the
 * tree hides the screen's dependency and makes the child untestable without
 * Koin. A default parameter value of a Root function counts as inside it.
 * Activity-level resolution (the `by viewModel()` delegate) is outside any
 * composable and out of scope.
 */
class KoinViewModelOnlyInRoot(config: Config) :
    Rule(
        config,
        "koinViewModel() and viewModel() are called only inside Root composables.",
    ) {
    @Configuration("name suffixes that mark a composable as a Root, the owner of a ViewModel")
    private val rootSuffixes: List<String> by config(listOf(ROOT))

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text
        if (callee !in VIEW_MODEL_CALLS) return

        val enclosingFunctions = expression.parents.filterIsInstance<KtNamedFunction>().toList()
        if (enclosingFunctions.none { enclosing -> enclosing.isComposable() }) return
        if (enclosingFunctions.any { enclosing -> rootSuffixes.any { enclosing.name?.endsWith(it) == true } }) return

        report(
            Finding(
                Entity.from(expression),
                "Resolve the ViewModel with '$callee()' in the Root composable and pass " +
                    "state and callbacks down instead.",
            ),
        )
    }

    private companion object {
        val VIEW_MODEL_CALLS = setOf("koinViewModel", "viewModel")
        const val ROOT = "Root"
    }
}
