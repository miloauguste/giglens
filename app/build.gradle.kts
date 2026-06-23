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
    // CORRECT: read the owasp.nvd.api.key Gradle property (passed via -P on the
    //          command line, e.g. from the pre-push hook) and wire it into the
    //          plugin's nvd{} block -- without this, -Powasp.nvd.api.key=... on
    //          the command line is silently ignored, and the scan runs fully
    //          unauthenticated against NVD's public (heavily rate-limited) API
    // WRONG:   assuming a -P property is automatically picked up by the plugin
    //          just because it's passed on the command line -- Gradle project
    //          properties must be explicitly read and assigned somewhere
    nvd {
        val key = project.findProperty("owasp.nvd.api.key") as String? ?: System.getenv("NVD_API_KEY") ?: ""
        if (key.isNotBlank()) {
            apiKey = key
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
        versionCode = 237
        versionName = "0.1.237"

        // SMTP credentials intentionally NOT in defaultConfig -- see buildTypes.debug
        // and buildTypes.release below for per-variant buildConfigField calls.
        // This prevents real Gmail App Password values from leaking into the
        // release APK's BuildConfig (extractable by decompilation).
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

            // CORRECT: real Gmail App Password values from env vars, read at debug
            //          build time only -- baked into the debug APK's BuildConfig.
            //          Debug builds are never distributed via Play Store, so the
            //          App Password never leaves the developer's machine in any
            //          shippable artifact.
            // WRONG:   declaring these in defaultConfig -- release builds would
            //          inherit the real values and ship the App Password in the
            //          Play Store APK, extractable by decompilation.
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

            // CORRECT: placeholder non-empty string "__RELEASE_NOOP__" -- field
            //          present so compileReleaseKotlin resolves DEBUG_SMTP_USER/PASS,
            //          but no real credential ever ships in the release APK.
            //          DebugOfferEmailer.sendAsync() no-ops in release via
            //          BuildConfig.DEBUG check; this placeholder is belt-and-
            //          suspenders defense in depth.
            // WRONG:   passing """"__RELEASE_NOOP__""""" (9 quotes, 4+5) -- Kotlin
            //          parses this as raw string with content "__RELEASE_NOOP__""
            //          (TWO trailing quote chars), AGP emits `"X"";`, Java sees
            //          unclosed string literal.
            //          Correct count is 8 (4+4), matching the debug pattern
            //          """"$value"""" -- same raw-string structure, just literal
            //          content instead of $interpolation.
            buildConfigField("String", "DEBUG_SMTP_USER", """"__RELEASE_NOOP__"""")
            buildConfigField("String", "DEBUG_SMTP_PASS", """"__RELEASE_NOOP__"""")
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

    // CORRECT: exclude duplicate META-INF notice/license files shipped by both
    //          android-mail and android-activation -- they're identical license
    //          text, not functional code, safe to drop one copy from the APK
    // WRONG:   leaving both -- mergeReleaseJavaResource fails with "2 files found
    //          with path 'META-INF/NOTICE.md'" since Android refuses to silently
    //          pick one when duplicate resource paths collide across dependencies
    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE.txt"
        }
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
