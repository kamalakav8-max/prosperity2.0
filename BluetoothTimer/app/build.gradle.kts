plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.bluetoothtimer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.bluetoothtimer"
        minSdk = 26
        // API 32 preserves BluetoothAdapter.disable() compatibility on Android 13+.
        // This app is distributed as a sideloaded APK, not through Google Play.
        targetSdk = 32
        versionCode = 2
        versionName = "1.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
