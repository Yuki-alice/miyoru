plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":plugin:runtime"))
    implementation(project(":plugin:api"))
    implementation(libs.kotlinx.io.core)
    implementation(libs.datastore.preferences)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.ktor.client.okhttp)
}

compose.desktop {
    application {
        mainClass = "app.miyoru.desktop.MainKt"
        nativeDistributions {
            // SQLite JDBC 需要 java.sql；jpackage 默认裁剪，需显式保留
            modules("java.sql", "java.naming", "java.management")
        }
    }
}
