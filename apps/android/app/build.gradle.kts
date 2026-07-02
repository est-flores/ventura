plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "systems.formula.ventura"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "systems.formula.ventura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"")
        }
        create("staging") {
            dimension = "environment"
            buildConfigField("String", "API_BASE_URL", "\"https://ventura-production-3c3a.up.railway.app\"")
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("../../../gen/kotlin")
            kotlin.srcDirs("../../../gen/kotlin")
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // ConnectRPC
    implementation("com.connectrpc:connect-kotlin:0.7.1")
    implementation("com.connectrpc:connect-kotlin-okhttp:0.7.1")
    implementation("com.connectrpc:connect-kotlin-google-java-ext:0.7.1")

    // Protobuf (full runtime — matches generated code)
    implementation("com.google.protobuf:protobuf-java:4.35.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.35.1")

    // HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Media3 / ExoPlayer — video playback + scroll-driven prefetch (DefaultPreloadManager.Builder)
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")

    // Coil — poster/thumbnail image loading for first-frame overlay
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Lifecycle / ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}