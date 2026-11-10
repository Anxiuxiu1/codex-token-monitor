plugins {
    id("com.android.application")
}

android {
    namespace = "com.axx.tokenmonitor"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.axx.tokenmonitor"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "1.9.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
