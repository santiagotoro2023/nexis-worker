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

// ── Embed BUILD_TIMESTAMP at compile time ───────────────────────────────────────────────────
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

// ── Dependencies ────────────────────────────────────────────────────────────────────────
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation("org.json:json:20240303")
}

// ── Generate Windows .ico from icon.png (required for MSI installer icon) ──────────────────────
val generateWindowsIco by tasks.registering {
    val pngFile = file("src/main/resources/icon.png")
    val icoFile = layout.buildDirectory.file("generated/icon.ico")
    inputs.file(pngFile)
    outputs.file(icoFile)
    doLast {
        val outputFile = icoFile.get().asFile
        outputFile.parentFile.mkdirs()
        val img = javax.imageio.ImageIO.read(pngFile)
        // Scale to 256x256 for ICO
        val scaled = java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g2d = scaled.createGraphics()
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                             java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g2d.drawImage(img, 0, 0, 256, 256, null)
        g2d.dispose()
        // Write ICO format
        outputFile.outputStream().use { out ->
            val bos = java.io.ByteArrayOutputStream()
            javax.imageio.ImageIO.write(scaled, "png", bos)
            val pngData = bos.toByteArray()
            // ICO header: reserved=0, type=1 (icon), count=1
            fun writeShort(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
            fun writeInt(v: Int)   { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
            writeShort(0); writeShort(1); writeShort(1) // ICONDIR
            // ICONDIRENTRY: width, height, colorCount, reserved, planes, bitCount, sizeInBytes, offset
            out.write(0) // width=256 encoded as 0
            out.write(0) // height=256 encoded as 0
            out.write(0) // colorCount
            out.write(0) // reserved
            writeShort(1) // planes
            writeShort(32) // bitCount
            writeInt(pngData.size)
            writeInt(22) // offset = 6 (ICONDIR) + 16 (ICONDIRENTRY)
            out.write(pngData)
        }
    }
}

// ── Native distributions (.deb) ─────────────────────────────────────────────────────────────────────────────
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
                iconFile.set(layout.buildDirectory.file("generated/icon.ico"))
                menuGroup      = "NeXiS"
                shortcut       = true
                dirChooser     = true
                upgradeUuid    = "A3B4C5D6-E7F8-4A1B-9C2D-3E4F5A6B7C8D"
            }

            modules("java.net.http", "java.security.jgss", "jdk.crypto.ec", "java.prefs")
        }
    }
}

tasks.named("packageMsi") { dependsOn(generateWindowsIco) }

// ── Inject Depends: into the generated .deb ──────────────────────────────────────────────────────
val debRuntimeDeps = "playerctl, libnotify-bin, xdg-utils, xclip"

tasks.register("packageDebWithDeps") {
    dependsOn("packageDeb")
    description = "Repack the .deb to inject Depends: $debRuntimeDeps"
    val buildDir = layout.buildDirectory
    doFirst {
        // Verify fakeroot is available before attempting to repack
        val result = project.exec {
            commandLine("which", "fakeroot")
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "fakeroot is required for packageDebWithDeps. Install with: sudo apt-get install fakeroot"
            )
        }
    }
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
