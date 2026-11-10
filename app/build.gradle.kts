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
        versionCode = 6
        versionName = "1.6"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
