package com.plcoding.prism.tooling.konsist

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * The control for every other check in this module.
 *
 * ScopeIntegrityTest asks what LEAKED INTO the scope. That question cannot be
 * answered by a filter that matches nothing — an empty result reads as "nothing
 * leaked", which is exactly what a correct scope looks like. So when konsist
 * began reporting `\`-separated paths on Windows and every `/`-anchored
 * comparison here stopped matching, the two cases written to catch a widened
 * scope both PASSED while the scope was, measured on a 26-module install, 308 of
 * 314 declarations of PRISM's own sources.
 *
 * These tests take the opposite approach and test the PREDICATE against known
 * inputs, both spellings, with no scope, no konsist parse and no consumer
 * project. A predicate that cannot match is then a red test rather than a green
 * one, on any platform, including the one that cannot reproduce the bug.
 */
class ScopePathSpellingTest {
    @Test
    fun `forwardSlashed leaves a posix path alone`() {
        assertThat("/app/src/main/kotlin/Foo.kt".forwardSlashed())
            .isEqualTo("/app/src/main/kotlin/Foo.kt")
    }

    @Test
    fun `forwardSlashed rewrites a windows path`() {
        assertThat("""\app\src\main\kotlin\Foo.kt""".forwardSlashed())
            .isEqualTo("/app/src/main/kotlin/Foo.kt")
    }

    @Test
    fun `an excluded root is recognised however the platform spells it`() {
        ProjectScope.EXCLUDED_ROOTS.forEach { root ->
            assertThat(ProjectScope.isExcluded("/$root/some/File.kt"))
                .isTrue()
            assertThat(ProjectScope.isExcluded("""\$root\some\File.kt"""))
                .isTrue()
        }
    }

    @Test
    fun `a prism-owned module is recognised however the platform spells it`() {
        ProjectScope.PRISM_OWNED_MODULES.forEach { module ->
            assertThat(ProjectScope.isExcluded("/$module/src/test/kotlin/Rule.kt"))
                .isTrue()
            val windows = module.replace('/', '\\')
            assertThat(ProjectScope.isExcluded("""\$windows\src\test\kotlin\Rule.kt"""))
                .isTrue()
        }
    }

    @Test
    fun `consumer source is not excluded in either spelling`() {
        assertThat(ProjectScope.isExcluded("/app/src/main/kotlin/MainActivity.kt")).isFalse()
        assertThat(ProjectScope.isExcluded("""\app\src\main\kotlin\MainActivity.kt""")).isFalse()
        assertThat(ProjectScope.isExcluded("/core/domain/src/main/kotlin/Run.kt")).isFalse()
    }

    /**
     * The exclusions are anchored, and must stay so.
     *
     * `.prism` bounded at both ends is why a consumer module called
     * `prism-rules` at the repository root is still their code. Without the
     * anchors this function would quietly widen in the other direction and hide
     * the consumer's own sources from every rule.
     */
    @Test
    fun `a module whose name merely contains an excluded root is still the consumers`() {
        assertThat(ProjectScope.isExcluded("/my-prism-setup/src/main/kotlin/A.kt")).isFalse()
        assertThat(ProjectScope.isExcluded("""\my-prism-setup\src\main\kotlin\A.kt""")).isFalse()
        assertThat(ProjectScope.isExcluded("/tooling/prism-rules-extra/src/main/kotlin/A.kt"))
            .isFalse()
    }
}
