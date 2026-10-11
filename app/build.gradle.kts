plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mczadvanced.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.mczadvanced.wear"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    // Play upload key, kept outside the repo: set these in ~/.gradle/gradle.properties or as
    // environment variables. Without them, release builds use the local debug key (sideloading).
    val uploadStoreFile = providers.gradleProperty("STOVE_UPLOAD_STORE_FILE")
        .orElse(providers.environmentVariable("STOVE_UPLOAD_STORE_FILE")).orNull
    signingConfigs {
        if (uploadStoreFile != null) {
            create("upload") {
                storeFile = file(uploadStoreFile)
                storePassword = providers.gradleProperty("STOVE_UPLOAD_STORE_PASSWORD")
                    .orElse(providers.environmentVariable("STOVE_UPLOAD_STORE_PASSWORD")).get()
                keyAlias = providers.gradleProperty("STOVE_UPLOAD_KEY_ALIAS")
                    .orElse(providers.environmentVariable("STOVE_UPLOAD_KEY_ALIAS")).get()
                keyPassword = providers.gradleProperty("STOVE_UPLOAD_KEY_PASSWORD")
                    .orElse(providers.environmentVariable("STOVE_UPLOAD_KEY_PASSWORD")).get()
            }
        }
    }

    buildTypes {
        release {
            // R8 + non-debuggable: Compose for Wear is noticeably laggy in debug builds.
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.concurrent.futures.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)
}
