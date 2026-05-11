import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Subtract a fixed base so versionCode stays small and never overflows Int.
// Base = 2023-11-15 00:00:00 UTC. Current value ≈47M; overflow ~2091.
private val BASE_EPOCH = 1_700_006_400L
val buildTime = System.getenv("BUILD_TIMESTAMP")?.toLongOrNull()
    ?: Instant.now().epochSecond
val versionCode = (buildTime - BASE_EPOCH).toInt()

android {
    namespace = "ch.toroag.nexis.worker"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.toroag.nexis.worker"
        minSdk = 26
        targetSdk = 35
        versionCode = versionCode
        versionName = "1.0.0"

        buildConfigField("long", "VERSION_TIMESTAMP", "${buildTime}L")
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Fail fast when KEYSTORE_PATH is absent in CI rather than silently producing
// an unsigned APK that will be rejected at distribution time.
tasks.named("assembleRelease") {
    doFirst {
        if (System.getenv("CI") != null && System.getenv("KEYSTORE_PATH") == null) {
            throw GradleException("KEYSTORE_PATH is required for release builds in CI")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
}
