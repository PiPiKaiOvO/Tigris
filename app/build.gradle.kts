plugins {
    id("com.android.application")
}

android {
    namespace = "com.leslie.tvhome"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.leslie.tvhome"
        minSdk = 28
        targetSdk = 31
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        // Sideloaded onto one TV, never published to Play.
        disable += "ExpiredTargetSdkVersion"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.palette:palette:1.0.0")
    implementation("dev.mobile:dadb:2.0.0")
}
