plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.bachors.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bachors.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        // Required by org.jellyfin.media3:media3-ffmpeg-decoder (AAR metadata / Java 8+ APIs on older devices)
        isCoreLibraryDesugaringEnabled = true
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            pickFirsts += "lib/**/libc++_shared.so"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.squareup.picasso)
    // ExoPlayer (Media3) - replaces legacy IjkPlayer which crashes on Android 16
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.3.1")
    // Software decode for AC-3 / E-AC-3 / DTS (Google does not publish media3-decoder-ffmpeg to Maven;
    // Jellyfin publishes a compatible build aligned with Media3 1.3.1. License: GPL-3.0.)
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2")
    
    // API Support
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Optional second engine (settings → پەخشکەر). LGPL — https://www.videolan.org/legal.html
    implementation("org.videolan.android:libvlc-all:3.6.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}