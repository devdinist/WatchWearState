plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devdinist.watchwear.wear"
    compileSdk = 35

    defaultConfig {
        // ⚠️ mobile 모듈과 applicationId가 반드시 같아야 Data Layer 통신이 됩니다.
        applicationId = "com.devdinist.watchwear"
        minSdk = 30          // Wear OS 3+
        targetSdk = 34       // Wear OS 5 (Android 14)
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
