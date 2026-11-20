import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `staticAnalysis` on the root project: ktlint and detekt over every module
 * that applies `tracky.ktlint` / `tracky.detekt`, plus build-logic's own sources.
 *
 *     plugins { id("tracky.static-analysis") }
 */
class StaticAnalysisConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            check(this == rootProject) {
                "tracky.static-analysis belongs on the root project: it aggregates every module."
            }

            val staticAnalysis =
                tasks.register(STATIC_ANALYSIS_TASK) {
                    group = VERIFICATION_GROUP
                    description = "Runs ktlint and detekt over the whole repository."
                    // build-logic is an included build, invisible to the subprojects loop.
                    dependsOn(gradle.includedBuild(BUILD_LOGIC).task(":$KTLINT_CHECK_TASK"))
                }

            subprojects {
                // Bound here: inside `staticAnalysis.configure` the receiver is the task.
                val module = path

                plugins.withId(KTLINT_PLUGIN_ID) {
                    staticAnalysis.configure { dependsOn("$module:$KTLINT_CHECK_TASK") }
                }
                plugins.withId(DETEKT_PLUGIN_ID) {
                    // The plain task is syntax-only; the variant tasks below add the
                    // type-resolving rules over main sources.
                    staticAnalysis.configure { dependsOn("$module:detekt") }
                    plugins.withId(ANDROID_BASE_PLUGIN_ID) {
                        staticAnalysis.configure { dependsOn("$module:detektDebug") }
                    }
                    plugins.withId(KOTLIN_JVM_PLUGIN_ID) {
                        staticAnalysis.configure { dependsOn("$module:detektMain") }
                    }
                    // The android `main` compilation of a KMP module includes commonMain.
                    plugins.withId(KMP_ANDROID_LIBRARY_PLUGIN_ID) {
                        staticAnalysis.configure { dependsOn("$module:detektMainAndroid") }
                    }
                }
            }
        }
    }

    private companion object {
        const val VERIFICATION_GROUP = "verification"
        const val STATIC_ANALYSIS_TASK = "staticAnalysis"
        const val KTLINT_CHECK_TASK = "ktlintCheck"
        const val BUILD_LOGIC = "build-logic"
        const val KTLINT_PLUGIN_ID = "tracky.ktlint"
        const val DETEKT_PLUGIN_ID = "tracky.detekt"
        const val ANDROID_BASE_PLUGIN_ID = "com.android.base"
        const val KOTLIN_JVM_PLUGIN_ID = "org.jetbrains.kotlin.jvm"
        const val KMP_ANDROID_LIBRARY_PLUGIN_ID = "com.android.kotlin.multiplatform.library"
    }
}
