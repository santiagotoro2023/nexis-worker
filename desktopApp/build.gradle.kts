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
val appVersion: String = System.getenv("VERSION") ?: "1.0.0"

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
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName        = "nexis-worker-desktop"
            packageVersion     = appVersion
            description        = "NeXiS Desktop Worker — companion app for the NeXiS AI controller"
            vendor             = "toroag.ch"
            licenseFile.set(rootProject.file("LICENSE"))

            linux {
                iconFile.set(file("src/main/resources/icon.png"))
                debMaintainer  = "santiagoleontororamirez@gmail.com"
                appCategory    = "Utility"
                menuGroup      = "Utility"
                shortcut       = true
                debPackageVersion = packageVersion
            }

            windows {
                iconFile.set(file("src/main/resources/icon.png"))
                menuGroup      = "NeXiS"
                shortcut       = true
                dirChooser     = true
                upgradeUuid    = "A3B4C5D6-E7F8-4A1B-9C2D-3E4F5A6B7C8D"
            }

            modules("java.net.http", "java.security.jgss", "jdk.crypto.ec", "java.prefs")
        }
    }
}

// ── Inject Depends: into the generated .deb ──────────────────────────────────
// The Compose Desktop DSL (1.7.3) does not expose --linux-package-deps, so we
// post-process the .deb with dpkg-deb after it's built.
val debRuntimeDeps = "playerctl, libnotify-bin, xdg-utils, xclip"

tasks.register("packageDebWithDeps") {
    dependsOn("packageDeb")
    description = "Repack the .deb to inject Depends: $debRuntimeDeps"
    val buildDir = layout.buildDirectory
    doLast {
        val debDir = buildDir.dir("compose/binaries/main/deb").get().asFile
        val deb    = debDir.listFiles()?.firstOrNull { it.extension == "deb" }
            ?: error("No .deb found in ${debDir.absolutePath}")

        val tmp = buildDir.dir("compose/deb-repack").get().asFile
        tmp.deleteRecursively(); tmp.mkdirs()

        project.exec { commandLine("dpkg-deb", "-R", deb.absolutePath, tmp.absolutePath) }

        val control = File(tmp, "DEBIAN/control")
        val text    = control.readText()
        if ("Depends:" !in text) {
            control.writeText(text.trimEnd() + "\nDepends: $debRuntimeDeps\n")
        }

        project.exec { commandLine("fakeroot", "dpkg-deb", "-b", tmp.absolutePath, deb.absolutePath) }
        tmp.deleteRecursively()
        println("Repacked: ${deb.name}  (Depends: $debRuntimeDeps)")
    }
}
