import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

group   = "ch.toroag.nexis"
version = "1.0.0"

kotlin {
    jvmToolchain(21)
}

// ── Embed BUILD_TIMESTAMP at compile time ──────────────────────────────────────
val buildTimestamp: String = System.getenv("BUILD_TIMESTAMP") ?: "0"

val generateBuildConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildconfig/kotlin")
    outputs.dir(outputDir)
    inputs.property("buildTimestamp", buildTimestamp)
    doLast {
        val dir = outputDir.get().asFile.also { it.mkdirs() }
        File(dir, "BuildConfig.kt").writeText(
            """
            package ch.toroag.nexis.desktop
            object BuildConfig {
                /** Unix timestamp of the CI build, 0 for local/dev builds. */
                const val VERSION_TIMESTAMP = ${buildTimestamp}L
            }
            """.trimIndent()
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBuildConfig)
}

// ── Dependencies ──────────────────────────────────────────────────────────────
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation("org.json:json:20240303")
}

// ── Native distributions (.deb) ───────────────────────────────────────────────
compose.desktop {
    application {
        mainClass = "ch.toroag.nexis.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName        = "nexis-worker-desktop"
            packageVersion     = "1.0.0"
            description        = "NeXiS Desktop Worker — companion app for the NeXiS AI controller"
            vendor             = "toroag.ch"
            licenseFile.set(rootProject.file("LICENSE"))

            linux {
                iconFile.set(file("src/main/resources/icon.png"))
                debMaintainer  = "santiagoleontororamirez@gmail.com"
                appCategory    = "Utility"
                menuGroup      = "Utility"
                shortcut       = true
            }

            modules("java.net.http", "java.security.jgss", "jdk.crypto.ec", "java.prefs")
        }
    }
}
