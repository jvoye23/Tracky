import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named

/**
 * Wires detekt into whichever project applies this plugin, against the single rule
 * set in the root `detekt.yml` (built on detekt's defaults) plus the third-party
 * Compose rules. The configuration file is the only place rules are tuned.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            pluginManager.apply("dev.detekt")

            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.layout.projectDirectory.file("detekt.yml"))
                buildUponDefaultConfig.set(true)
                // detekt's default source set omits src/androidTest, where every
                // instrumentation suite lives, so the set is declared explicitly.
                source.setFrom(
                    files(
                        "src/main/java",
                        "src/main/kotlin",
                        "src/test/java",
                        "src/test/kotlin",
                        "src/androidTest/java",
                        "src/androidTest/kotlin",
                    ),
                )
                // A Kotlin Multiplatform module keeps its code in src/<sourceSet>/kotlin
                // (commonMain, androidMain, iosTest, ...): add every source set on disk.
                pluginManager.withPlugin(KOTLIN_MULTIPLATFORM_PLUGIN_ID) {
                    source.from(
                        layout.projectDirectory
                            .dir("src")
                            .asFile
                            .listFiles { file -> file.isDirectory }
                            .orEmpty()
                            .flatMap { sourceSet -> listOf(sourceSet.resolve("kotlin"), sourceSet.resolve("java")) }
                            .filter { it.isDirectory },
                    )
                }
                basePath.set(rootProject.layout.projectDirectory)
                // Per module; detekt passes it only when the file exists.
                baseline.set(layout.projectDirectory.file(BASELINE_FILE))
            }

            // The type-resolving KMP tasks analyse whole compilations, including KSP
            // output such as Room's *_Impl.kt. Generated code is not ours to lint.
            tasks.withType(Detekt::class.java).configureEach {
                exclude { it.file.invariantSeparatorsPath.contains(GENERATED_OUTPUT_SEGMENT) }
            }
            tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
                exclude { it.file.invariantSeparatorsPath.contains(GENERATED_OUTPUT_SEGMENT) }
            }

            dependencies.add(DETEKT_PLUGINS_CONFIGURATION, libs.findLibrary("compose-rules-detekt").get())
        }
    }

    private companion object {
        const val DETEKT_PLUGINS_CONFIGURATION = "detektPlugins"
        const val GENERATED_OUTPUT_SEGMENT = "/build/"
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
        const val BASELINE_FILE = "detekt.baseline.xml"
    }
}
