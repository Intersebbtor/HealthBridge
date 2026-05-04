plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.healthbridge.android"
    compileSdk = 34
    
    defaultConfig {
        applicationId = "com.healthbridge.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                implementation("com.squareup.okhttp3:okhttp:4.12.0")
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.fragment:fragment-ktx:1.6.2")
                implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
                implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.0")
                val cameraxVersion = "1.3.1"
                implementation("androidx.camera:camera-camera2:$cameraxVersion")
                implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
                implementation("androidx.camera:camera-view:$cameraxVersion")
                implementation("com.google.guava:guava:33.0.0-android")
            }
        }
    }
}
