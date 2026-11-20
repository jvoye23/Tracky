import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Bundling
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Wires ktlint into whichever project applies this plugin.
 *
 *  - `ktlintCheck` — read-only, fails the build on any violation.
 *  - `ktlintFormat` — the same scan with `--format`, autocorrecting in place.
 *
 * The whole rule set lives in the root `.editorconfig`, so Gradle and the IDE
 * see the same configuration.
 */
class KtlintConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            configurations.resolvable(KTLINT_CONFIGURATION) {
                // ktlint-cli publishes a plain and a shadowed runtime variant; the
                // shadowed one is a single self-contained jar.
                attributes {
                    attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
                }
            }
            dependencies.add(KTLINT_CONFIGURATION, libs.findLibrary("ktlint-cli").get())

            tasks.register<JavaExec>("ktlintCheck") {
                group = "verification"
                description = "Checks Kotlin sources in this project against the root .editorconfig rule set."
                classpath = configurations.getByName(KTLINT_CONFIGURATION)
                mainClass.set(KTLINT_MAIN_CLASS)
                workingDir = projectDir
                args = SOURCE_PATTERNS + REPORTING_ARGS
            }

            tasks.register<JavaExec>("ktlintFormat") {
                group = "formatting"
                description = "Autocorrects Kotlin sources in this project and reports what could not be fixed."
                classpath = configurations.getByName(KTLINT_CONFIGURATION)
                mainClass.set(KTLINT_MAIN_CLASS)
                workingDir = projectDir
                args = SOURCE_PATTERNS + REPORTING_ARGS + "--format"
                // Formatting must not stop at a violation that has no autocorrect;
                // ktlintCheck is the check, this task only repairs.
                isIgnoreExitValue = true
            }
        }
    }

    private companion object {
        const val KTLINT_CONFIGURATION = "ktlint"
        const val KTLINT_MAIN_CLASS = "com.pinterest.ktlint.Main"

        val SOURCE_PATTERNS =
            listOf(
                "src/**/*.kt",
                "src/**/*.kts",
                "*.kts",
                "!**/build/**",
                "!**/generated/**",
            )

        val REPORTING_ARGS =
            listOf(
                "--relative",
                "--reporter=plain",
            )
    }
}
