import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun localString(name: String, fallback: String): String =
    localProperties.getProperty(name, fallback).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.lr_1"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.lr_1"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "CLOUD_BASE_URL",
            "\"${localString("CLOUD_BASE_URL", "https://jsonplaceholder.typicode.com/")}\"",
        )
        buildConfigField(
            "String",
            "CLOUD_UPLOAD_PATH",
            "\"${localString("CLOUD_UPLOAD_PATH", "posts")}\"",
        )
        buildConfigField(
            "String",
            "CLOUD_COMMAND_PATH",
            "\"${localString("CLOUD_COMMAND_PATH", "todos/19")}\"",
        )
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
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
