// App-level build for GigLens
// Author: Claude (Anthropic)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.owasp.dependencycheck")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "/home/poppa/giglens/dependency-check-suppressions.xml"
    analyzers {
        ossIndex {
            enabled = false
        }
    }
}
android {
    namespace = "com.augusteenterprise.giglens"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.augusteenterprise.giglens"
        minSdk = 26
        targetSdk = 35
        versionCode = 163
        versionName = "0.1.163"
    }

    signingConfigs {
        create("release") {
            storeFile = file("/home/poppa/giglens/giglens-release.keystore")
            storePassword = System.getenv("GIGLENS_STORE_PASSWORD") ?: ""
            keyAlias = "giglens"
            keyPassword = System.getenv("GIGLENS_KEY_PASSWORD") ?: ""
        }
    }
    buildTypes {
        debug {
            // CORRECT: sign debug with release keystore — avoids signature conflict on device
            // WRONG:   default debug keystore — conflicts with Play Store release install
            signingConfig = signingConfigs.getByName("release")

            // CORRECT: SMTP creds via env var, same pattern as GIGLENS_STORE_PASSWORD above --
            //          never committed, never hardcoded in this file
            // WRONG:   hardcoding credentials here or in DebugOfferEmailer.kt directly --
            //          would ship inside the debug APK, decompilable
            val debugSmtpUser = System.getenv("GIGLENS_DEBUG_SMTP_USER") ?: ""
            val debugSmtpPass = System.getenv("GIGLENS_DEBUG_SMTP_PASS") ?: ""
            buildConfigField("String", "DEBUG_SMTP_USER", """"$debugSmtpUser"""")
            buildConfigField("String", "DEBUG_SMTP_PASS", """"$debugSmtpPass"""")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // Android core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // Google ML Kit - OCR text recognition
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Room database (replaces raw SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // RecyclerView for offer history list
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // JavaMail (Android-compatible build) -- used by DebugOfferEmailer for
    // direct SMTP send, debug builds only (see BuildConfig.DEBUG gate in
    // DebugOfferEmailer.kt). Standard javax.mail does not run on Android --
    // these are the Android-packaged forks.
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Firebase BoM — manages all Firebase library versions
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    // Crashlytics + Analytics (required for Crashlytics logs feature)
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    // Force patched protobuf version — fixes CVE-2022-3171, CVE-2024-7254 from Firebase transitive deps
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")
}
