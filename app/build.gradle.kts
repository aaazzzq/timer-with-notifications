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
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" }
}

dependencies {
    // --- Compose BOM + core ---
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // icons (still from phone-Compose)
    implementation("androidx.compose.material:material-icons-extended")

    // --- Wear Compose 1.4.1 ---
    implementation("androidx.wear.compose:compose-material:1.4.1")
    implementation("androidx.wear.compose:compose-foundation:1.4.1")
    implementation("androidx.wear.compose:compose-navigation:1.4.1")
    // (optional tooling for previews)
    implementation("androidx.wear.compose:compose-ui-tooling:1.4.1")

    // lifecycle & viewmodel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // regular navigation (if you need it)
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // serialization + coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-splashscreen:1.0.1") // Use the latest stable version (1.0.1 as of writing)
}
