plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "tv.danmaku.ijk.media.player"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        targetSdk = 35
        consumerProguardFiles("proguard-rules.pro")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
