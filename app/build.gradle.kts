// App-level build for GigLens
// Author: Claude (Anthropic)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.owasp.dependencycheck")
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
        targetSdk = 34
        versionCode = 24
        versionName = "0.1.23"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("com.google.mlkit:text-recognition:16.0.0")

    // Room database (replaces raw SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // RecyclerView for offer history list
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.google.android.gms:play-services-location:21.2.0")
}
