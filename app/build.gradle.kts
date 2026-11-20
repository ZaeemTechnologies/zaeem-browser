plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.zaeem.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zaeemtechnologies.browser"
        minSdk = 29          // platform BiometricManager/BiometricPrompt, secure Keystore AES-GCM
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Stable release signing comes from CI secrets; otherwise falls back to the debug key (installable, but
    // a different signature each fresh CI keystore means updates will not install over older builds).
    val ksPath = System.getenv("ZAEEM_KEYSTORE")
    signingConfigs {
        if (ksPath != null) create("release") {
            storeFile = file(ksPath)
            storePassword = System.getenv("ZAEEM_KS_PASS")
            keyAlias = System.getenv("ZAEEM_KEY_ALIAS")
            keyPassword = System.getenv("ZAEEM_KS_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false     // not enabled until R8 output has been tested on a device
            signingConfig = if (ksPath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.webkit:webkit:1.13.0")   // Apache-2.0: WebView profiles + document-start scripts
}
