import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI passes the GitHub Actions run number; it becomes the version code the updater compares.
val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.msgs.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.msgs.app"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        buildConfigField("String", "UPDATE_REPO", "\"thomasharbert20-gif/msgs\"")
        // R4 test build: no deleting on the phone, no self-updating.
        buildConfigField("boolean", "ALLOW_DELETE", "false")
        buildConfigField("boolean", "UPDATER_ENABLED", "false")
    }

    // The signing key lives outside the repo. CI writes it to a temp file and passes
    // its path and password through the environment (see ci/build.sh).
    signingConfigs {
        create("release") {
            storeFile = System.getenv("MSGS_KEYSTORE")?.let { rootProject.file(it) }
            storePassword = System.getenv("MSGS_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "msgs"
            keyPassword = System.getenv("MSGS_KEYSTORE_PASSWORD") ?: ""
            storeType = "pkcs12"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // The Go engine, compiled by gomobile in CI (see ci/build.sh).
    implementation(files("libs/gmbridge.aar"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.zxing:core:3.5.3")
}
