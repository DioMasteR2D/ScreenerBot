plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.screenerbot.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.screenerbot.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.26-android"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:ext:junit:1.2.1")
}
