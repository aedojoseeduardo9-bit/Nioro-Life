plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nioro.life"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nioro.life"
        minSdk = 26
        targetSdk = 35
        // GitHub numera cada compilación; la app compara este número para saber si hay una versión nueva.
        versionCode = (System.getenv("BUILD_NUMBER") ?: "3").toInt()
        versionName = "2.3"
    }

    // Firma fija: sin ella, Android no deja actualizar la app encima de la anterior.
    signingConfigs {
        create("nioro") {
            storeFile = rootProject.file("nioro.keystore")
            storePassword = "nioro-life"
            keyAlias = "nioro"
            keyPassword = "nioro-life"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("nioro")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("nioro")
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
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
}
