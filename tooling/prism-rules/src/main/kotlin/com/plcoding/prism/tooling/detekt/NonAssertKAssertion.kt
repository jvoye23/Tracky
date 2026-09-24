package com.plcoding.prism.tooling.detekt

import dev.detekt.api.Config
import dev.detekt.api.Configuration
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.config
import org.jetbrains.kotlin.psi.KtImportDirective

/**
 * Reports an assertion import that is not AssertK.
 *
 * Assertions in this codebase go through AssertK. `kotlin.test`,
 * `org.junit.Assert`, and jupiter's `Assertions` all sneak in through IDE
 * auto-import and fracture the assertion style. The jupiter exception-assertion
 * helpers stay allowed because AssertK has no equivalent that returns the
 * thrown value with the same ergonomics.
 *
 * Only kotlin.test's ASSERTION functions are reported, not the package. Its
 * `@Test`, `@BeforeTest` and `@AfterTest` are the only test annotations a
 * multiplatform `commonTest` source set has, and AssertK does not replace them;
 * banning the whole package reported every KMP test file with nothing to fix.
 */
class NonAssertKAssertion(config: Config) :
    Rule(
        config,
        "Test assertions go through AssertK, not kotlin.test or JUnit assertion APIs.",
    ) {
    @Configuration("jupiter top-level assertion helpers that remain allowed")
    private val allowedJupiterAssertions: List<String> by config(
        listOf("assertThrows", "assertDoesNotThrow"),
    )

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val imported = importDirective.importedFqName?.asString() ?: return
        val banned =
            BANNED_PREFIXES.any { bannedPrefix -> imported.startsWith(bannedPrefix) } ||
                isBannedKotlinTestMember(imported) ||
                isBannedJupiterHelper(imported)
        if (!banned) return
        report(
            Finding(
                Entity.from(importDirective),
                "Replace '$imported' with an AssertK assertion (assertk.assertThat).",
            ),
        )
    }

    private fun isBannedKotlinTestMember(imported: String): Boolean {
        if (!imported.startsWith(KOTLIN_TEST_PREFIX)) return false
        val member = imported.removePrefix(KOTLIN_TEST_PREFIX)
        return member.startsWith(HELPER_PREFIX) || member in KOTLIN_TEST_ASSERTION_FUNCTIONS
    }

    private fun isBannedJupiterHelper(imported: String): Boolean {
        if (!imported.startsWith(JUPITER_API_PREFIX)) return false
        val member = imported.substringAfterLast('.')
        val isAssertionHelper = member.startsWith(HELPER_PREFIX) || member == FAIL_HELPER
        return isAssertionHelper && member !in allowedJupiterAssertions
    }

    private companion object {
        val BANNED_PREFIXES =
            listOf("org.junit.Assert", "org.junit.jupiter.api.Assertions")
        const val KOTLIN_TEST_PREFIX = "kotlin.test."
        val KOTLIN_TEST_ASSERTION_FUNCTIONS = setOf("fail", "expect", "todo")
        const val JUPITER_API_PREFIX = "org.junit.jupiter.api."
        const val HELPER_PREFIX = "assert"
        const val FAIL_HELPER = "fail"
    }
}
