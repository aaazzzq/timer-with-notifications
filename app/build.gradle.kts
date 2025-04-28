plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.example.timerapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.timerapp"
        minSdk        = 30
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"
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
    }
    composeOptions {
        // Use the Compose Compiler that matches your Compose BOM
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packagingOptions {
        // (optional) avoid duplicate-file errors, e.g. from wear previews
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ---- Compose BOM (keeps Compose libraries in sync) ----
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))

    // Core Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Material 3 (for TextField, AlertDialog, etc.)
    implementation("androidx.compose.material3:material3")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // ---- Wear-OS Compose (v1) ----
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")
    // Optional tooling for Wear previews
    implementation("androidx.wear.compose:compose-ui-tooling:1.4.1")

    // Lifecycle & ViewModel support in Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // Navigation (if you use Navigation-Compose)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Kotlin serialization & coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // AndroidX SplashScreen (optional)
    implementation("androidx.core:core-splashscreen:1.0.1")
}
