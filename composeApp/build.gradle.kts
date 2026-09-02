import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}

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
            """.trimMargin()
        )
    }
}

kotlin {
    androidLibrary {
        compileSdk = 37
        minSdk = 33
        namespace = "com.jvcs.tracky.composeapp"
        experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    

    
    sourceSets {
        commonMain {
            kotlin.srcDir(tasks.named("generateApiConfig").map { it.outputs.files.singleFile })
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)

        }
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(compose.uiTooling)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.koin.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.work.runtime)
            implementation(libs.androidx.lifecycle.process)

        }
        commonMain.dependencies {
            //implementation(compose.ui)
            implementation(libs.datastore)
            implementation(libs.datastore.preferences)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            //implementation("org.jetbrains.compose.ui:ui-backhandler:${libs.versions.composeMultiplatform.get()}")
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
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            // Dispatchers.setMain, so ViewModels backed by viewModelScope are testable off-device.
            implementation(libs.kotlinx.coroutines.test)
            // Ordered, suspending assertions on event flows; fails on unconsumed emissions.
            implementation(libs.turbine)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }
    }
}

// Compose Multiplatform 1.12.0 pulls org.jetbrains.androidx.navigationevent:navigationevent-compose
// 1.1.0, which is an empty forwarder to the AndroidX artifact it depends on. Both publish a klib
// named `navigationevent-compose_commonMain`, and the metadata transformation keeps only one of
// them -- the empty one wins, so androidx.navigationevent.compose vanishes from commonMain while
// still resolving on the Android and JVM targets. Drop the forwarder; the real artifact is declared
// above.
configurations.configureEach {
    exclude(group = "org.jetbrains.androidx.navigationevent", module = "navigationevent-compose")
}

room{
    schemaDirectory("${projectDir}/schemas")
}

dependencies {
    // KSP support for Room Compiler.
    // KMP has no generic `ksp` configuration; each target needs its own.
    add("kspAndroid", libs.room.compiler)
    add("kspJvm", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}


compose.desktop {
    application {
        mainClass = "com.jvcs.tracky.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.jvcs.tracky"
            packageVersion = "1.0.0"
        }
    }
}
