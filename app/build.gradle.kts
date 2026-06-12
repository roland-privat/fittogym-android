plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

import java.util.Properties

// Read signing credentials from keystore.properties (gitignored). The file
// must live at the project root and define:
//   storeFile=<absolute or relative path to .jks>
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
// When the file is missing (typical for clean clones / CI without secrets)
// the release variant falls back to using the debug keystore so the project
// still builds end-to-end — but the resulting AAB is NOT uploadable to Play.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.runtraining"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fittogym.runtraining"
        minSdk = 31
        targetSdk = 35
        // versionCode + versionName are read from gradle.properties so a
        // release bump touches a single line.
        versionCode = (project.findProperty("app.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("app.versionName") as String?) ?: "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Keep debug applicationId == release so the user's existing
            // imported workouts on their dev device survive the variant
            // changes. Add `applicationIdSuffix = ".debug"` later only when
            // you need to install debug + release side by side.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the real signing config when keystore.properties is present,
            // otherwise fall back to debug so `assembleRelease` still completes.
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Point Android source sets at src/main/kotlin instead of src/main/java
    // so Kotlin-first layout is the default.
    sourceSets {
        getByName("main").kotlin.srcDirs("src/main/kotlin")
        getByName("test").kotlin.srcDirs("src/test/kotlin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export: committed under app/schemas/ so future migrations can
// be diff-reviewed. NOT in .gitignore (per data-model.md §6 migration policy).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    // Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // FIT decoder (Maven Central — Garmin Java SDK)
    implementation(libs.fit)

    // Tests (JVM unit tests in app/src/test/, optional per spec)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}
