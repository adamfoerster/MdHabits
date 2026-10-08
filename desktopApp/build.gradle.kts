import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Keep in sync with Android versionName and iOS MARKETING_VERSION (see AGENTS.md).
val appVersion = "0.18.0"

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.adamfoerster.mdhabits.MainKt"
        jvmArgs += "-Dmdhabits.version=$appVersion"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "MdHabits"
            packageVersion = appVersion
            // macOS packaging rejects a 0.x.y major version.
            macOS { packageVersion = "1.${appVersion.substringAfter('.')}" }
        }
    }
}
