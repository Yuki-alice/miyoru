plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.hot.reload)
}

kotlin {
    // 热重载依赖 JBR 的增强类重定义能力，toolchain 对齐 21
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":plugin:runtime"))
    implementation(project(":plugin:api"))
    implementation(libs.kotlinx.io.core)
    implementation(libs.datastore.preferences)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.ktor.client.okhttp)
    // mpv 原生库按构建机平台打入运行时；mediamp 在启动时从 classpath 解压加载。
    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val arm64 = osArch.contains("aarch64") || osArch.contains("arm64")
    when {
        osName.contains("win") && arm64 -> runtimeOnly(libs.mediamp.mpv.runtime.windows.arm64)
        osName.contains("win") -> runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
        osName.contains("mac") && arm64 -> runtimeOnly(libs.mediamp.mpv.runtime.macos.arm64)
        osName.contains("mac") -> runtimeOnly(libs.mediamp.mpv.runtime.macos.x64)
        osName.contains("linux") -> runtimeOnly(libs.mediamp.mpv.runtime.linux.x64)
        else -> logger.warn("Unsupported desktop OS for bundled mpv runtime: $osName ($osArch)")
    }
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

// 播放器验证：用真实视频源跑一遍桌面播放链路并截图，供本地/网络视频回归使用。
tasks.register<JavaExec>("playerVerify") {
    group = "verification"
    description = "播放真实视频（本地文件或网络 URL）并保存窗口截图"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.miyoru.desktop.PlayerVerifyKt")
    args(
        providers.gradleProperty("player.url").getOrElse(""),
        providers.gradleProperty("player.shot").getOrElse("player-verify.png"),
        providers.gradleProperty("player.wait").getOrElse("12000"),
    )
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    systemProperty("java.awt.headless", "false")
}
