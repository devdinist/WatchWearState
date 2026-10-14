plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devdinist.watchwear.mobile"
    compileSdk = 35

    defaultConfig {
        // ⚠️ wear 모듈과 applicationId가 반드시 같아야 Data Layer 통신이 됩니다.
        applicationId = "com.devdinist.watchwear"
        minSdk = 21
        // ⚠️ 의도적으로 22. API 23+를 타깃하면 삼성 비공개 system 키(진동 세기)를
        // WRITE_SECURE_SETTINGS가 있어도 쓸 수 없음 ("You cannot keep your settings in the secure settings").
        // Android 14+에선 설치 시 adb install --bypass-low-target-sdk-block 필요.
        targetSdk = 22
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes { release { isMinifyEnabled = false } }
    lint { disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
}
