plugins {
    id("com.android.application")
}

android {
    namespace = "dev.phoebe.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.phoebe.app"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("PHOEBE_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("PHOEBE_VERSION_NAME") ?: "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
}
