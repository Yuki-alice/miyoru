plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// AGP 9.5 实测：kotlin.android 仍是编译 Kotlin 源码所必需（"内置 Kotlin 支持"仅免 Java/Kotlin 混合配置）。

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "app.miyoru"
    compileSdk = 37
    signingConfigs {
        // 发布验证用 debug 密钥签名；正式商店包需替换为正式签名配置（待办）。
        getByName("debug") {
            storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    defaultConfig {
        applicationId = "app.miyoru"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":plugin:runtime"))
    implementation(project(":plugin:api"))
    implementation(libs.kotlinx.io.core)
    implementation(libs.datastore.preferences)
    implementation(compose.ui)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.koin.core)
    implementation(libs.ktor.client.okhttp)
}
