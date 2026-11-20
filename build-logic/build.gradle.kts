plugins {
    `kotlin-dsl`
}

// build-logic is an included build, so it cannot apply its own `tracky.ktlint`
// plugin. The tasks are registered here by hand against the same rule set.
val ktlint: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    // ktlint-cli publishes both a plain and a shadowed runtime variant. The
    // shadowed one is a single self-contained jar.
    attributes {
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
}

dependencies {
    ktlint(libs.ktlint.cli)
    implementation(libs.detekt.gradle.plugin)

    // compileOnly: JacocoConventionPlugin only touches AGP types inside
    // pluginManager.withPlugin callbacks. Coordinates rather than catalog aliases,
    // because build-logic needs the plugin jars, not the plugins.
    compileOnly("com.android.tools.build:gradle:${libs.versions.agp.get()}")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        register("jacoco") {
            id = "tracky.jacoco"
            implementationClass = "JacocoConventionPlugin"
        }
        register("ktlint") {
            id = "tracky.ktlint"
            implementationClass = "KtlintConventionPlugin"
        }
        register("detekt") {
            id = "tracky.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("staticAnalysis") {
            id = "tracky.static-analysis"
            implementationClass = "StaticAnalysisConventionPlugin"
        }
    }
}

val ktlintSourcePatterns =
    listOf(
        "src/**/*.kt",
        "src/**/*.kts",
        "*.kts",
        "!**/build/**",
        "!**/generated/**",
    )
val ktlintReportingArgs = listOf("--relative", "--reporter=plain")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "Checks build-logic sources against the root .editorconfig rule set."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args = ktlintSourcePatterns + ktlintReportingArgs
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "formatting"
    description = "Autocorrects build-logic sources and reports what could not be fixed."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    workingDir = projectDir
    args = ktlintSourcePatterns + ktlintReportingArgs + "--format"
    isIgnoreExitValue = true
}
