plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.casio.calc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.casio.calc"
        minSdk = 24
        targetSdk = 34
        versionCode = 16
        versionName = "4.9"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../casio.keystore")
            storePassword = "casio123456"
            keyAlias = "casio"
            keyPassword = "casio123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 正式包显式关闭调试，杜绝 debuggable APK 外流
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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