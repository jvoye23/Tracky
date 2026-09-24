package com.plcoding.prism.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports an HTTP verb called straight on the client.
 *
 * `safeGet` / `safePost` / `safeDelete` are the canonical wrappers: they convert
 * a transport throw into a typed error and map the status code once, in one
 * place. Calling the verb directly re-implements that at the call site, usually
 * incompletely.
 *
 * The module that implements the wrappers is excluded by configuration — it has
 * to call the verbs to wrap them.
 *
 * Two shapes are configurable, because projects spell their wrappers differently:
 * `safeWrappers` names block wrappers (`safeCall { client.get { } }`), and
 * `safeOverloadArgument` names an argument that only the project's own verb
 * overloads take — `client.post<Req, Res>(route = "...", body = ...)` is a safe
 * extension that happens to share Ktor's verb name, and without type resolution
 * the argument is what tells the two apart.
 */
class KtorCallMustUseSafeCall(config: Config) :
    Rule(
        config,
        "HTTP calls go through the project's safeGet / safePost / safeDelete wrappers.",
    ) {
    @Configuration("block wrappers inside which a raw verb call is the sanctioned form")
    private val safeWrappers: List<String> by config(DEFAULT_SAFE_WRAPPERS)

    @Configuration("named argument that marks a call as the project's own safe verb overload; empty disables")
    private val safeOverloadArgument: String by config("")

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        // No type resolution here, so the receiver is matched by the name the project
        // gives its client everywhere. A differently named client would slip past, which
        // is why the wrappers are also the reviewed convention rather than only a rule.
        if (expression.receiverExpression.text !in CLIENT_RECEIVERS) return
        val call = expression.selectorExpression as? KtCallExpression ?: return
        val verb = call.calleeExpression?.text ?: return
        if (verb !in HTTP_VERBS) return
        if (safeOverloadArgument.isNotEmpty() &&
            call.valueArguments.any { it.getArgumentName()?.asName?.asString() == safeOverloadArgument }
        ) {
            return
        }
        // A verb called inside safeCall { } is going through the wrapper -- that is the
        // sanctioned form, and the wrapper needs the verb to wrap.
        if (expression.parents
                .filterIsInstance<KtCallExpression>()
                .any { it.calleeExpression?.text in safeWrappers }
        ) {
            return
        }
        report(
            Finding(
                Entity.from(expression),
                "Call this through safe${verb.replaceFirstChar { it.uppercase() }} so the transport " +
                    "error and the status code are mapped in one place.",
            ),
        )
    }

    private companion object {
        val CLIENT_RECEIVERS = setOf("httpClient", "client")
        val HTTP_VERBS = setOf("get", "post", "put", "patch", "delete")
        val DEFAULT_SAFE_WRAPPERS = listOf("safeCall", "safeGet", "safePost", "safePut", "safePatch", "safeDelete")
    }
}
