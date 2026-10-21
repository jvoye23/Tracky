import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("prism.ktlint")
    id("prism.detekt")
    id("prism.jacoco")
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) load(file.inputStream())
    }

val xcodeDeveloperDir =
    providers
        .exec { commandLine("xcode-select", "-p") }
        .standardOutput
        .asText
        .map { it.trim() }

val generateApiConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/apiConfig/kotlin")
    val baseUrl = localProperties["BASE_URL"]?.toString() ?: ""
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile.resolve("com/jvcs/tracky/core/data/networking")
        dir.mkdirs()
        dir.resolve("ApiConfig.kt").writeText(
            """
            |package com.jvcs.tracky.core.data.networking
            |
            |internal object ApiConfig {
            |    const val BASE_URL = "$baseUrl"
            |}
            """.trimMargin(),
        )
    }
}

kotlin {
    android {
        compileSdk = 37
        minSdk = 33
        namespace = "com.jvcs.tracky.composeapp"
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
        // Pinned to androidApp's target. Left to the toolchain default (25), this library emitted
        // bytecode the app cannot inline, which broke as soon as an app test called safeCall.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        // commonTest also runs on a device, instrumented, so that it has a coverage number: JaCoCo
        // cannot measure the iOS simulator run.
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            enableCoverage = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // RevenueCat's iOS core is Swift. The app links through Xcode, which brings the Swift
        // runtime along; the Kotlin/Native test executable links without Xcode, so it needs the
        // active toolchain's Swift compatibility libraries on its search path.
        if (System.getProperty("os.name").startsWith("Mac")) {
            val swiftPlatform = if (iosTarget.name.contains("Simulator")) "iphonesimulator" else "iphoneos"
            iosTarget.binaries.withType<TestExecutable>().configureEach {
                linkerOpts(
                    "-L${xcodeDeveloperDir.get()}/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/$swiftPlatform",
                )
            }
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(tasks.named("generateApiConfig").map { it.outputs.files.singleFile })
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        // RevenueCat's iOS bindings are generated cinterop code, so every iOS source set opts in.
        named { it.lowercase().startsWith("ios") }.configureEach {
            languageSettings {
                optIn("kotlinx.cinterop.ExperimentalForeignApi")
            }
        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            // Android only: iOS calls the Firebase Swift SDK directly, see FirebaseAnalyticsBackend.swift.
            implementation(libs.gitlive.firebase.analytics)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.core.ktx)
        }
        commonMain.dependencies {
            // implementation(compose.ui)
            implementation(libs.datastore)
            implementation(libs.datastore.preferences)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            // implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.composeMultiplatform.get()}")
            implementation(libs.compose.ui.backhandler)
            implementation(compose.components.resources)
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kermit)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.coil.compose)
            implementation(libs.coil.network)
            implementation(libs.material.icons.extended)

            implementation(libs.navigation.compose)
            implementation(libs.androidx.navigationevent.compose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.adaptive)
            implementation(libs.kotlinx.datetime)
            implementation(libs.uuid)

            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.lifecycle.viewmodel.nav3)
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)

            implementation(libs.moko.permissions)
            implementation(libs.moko.permissions.notifications)

            implementation(libs.purchases.core)
            implementation(libs.purchases.result) // Optional
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Dispatchers.setMain, so ViewModels backed by viewModelScope are testable off-device.
            implementation(libs.kotlinx.coroutines.test)
            // Ordered, suspending assertions on event flows; fails on unconsumed emissions.
            implementation(libs.turbine)
            // Fluent assertions; the one assertion API PRISM's NonAssertKAssertion allows.
            implementation(libs.assertk)
            // A scripted HTTP engine, for the data sources whose behaviour is the status code
            // itself — a 204 that means "nothing is running", a 409 that carries an answer.
            implementation(libs.ktor.client.mock)
            // Renders composables headlessly (runComposeUiTest), so screens are tested alongside
            // their view models. Runs on the iOS simulator target.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(libs.compose.ui.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            // Declares the ComponentActivity that runComposeUiTest launches.
            implementation(libs.androidx.compose.ui.test.manifest)
        }
    }
}

// Compose Multiplatform 1.12.0 pulls org.jetbrains.androidx.navigationevent:navigationevent-compose
// 1.1.0, which is an empty forwarder to the AndroidX artifact it depends on. Both publish a klib
// named `navigationevent-compose_commonMain`, and the metadata transformation keeps only one of
// them -- the empty one wins, so androidx.navigationevent.compose vanishes from commonMain while
// still resolving on the Android target. Drop the forwarder; the real artifact is declared
// above.
configurations.configureEach {
    exclude(group = "org.jetbrains.androidx.navigationevent", module = "navigationevent-compose")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // KSP support for Room Compiler.
    // KMP has no generic `ksp` configuration; each target needs its own.
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}
