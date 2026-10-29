import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// Keep in sync with Android versionName and iOS MARKETING_VERSION (see AGENTS.md).
val appVersion = "0.21.2"

dependencies {
    implementation(projects.shared)
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.adamfoerster.mdhabits.MainKt"
        jvmArgs += "-Dmdhabits.version=$appVersion"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "MdHabits"
            packageVersion = appVersion
            // macOS packaging rejects a 0.x.y major version.
            macOS { packageVersion = "1.${appVersion.substringAfter('.')}" }
            // Never change: it lets a newer Windows installer upgrade the installed app in place.
            windows { upgradeUuid = "dacf405b-1517-4eb7-a42d-465aa62b1c7f" }
        }
    }
}

// Windows only: builds the installer (WiX Toolset required by jpackage) and copies the
// resulting MdHabits-<version>.exe to desktopApp/build/windows.
tasks.register<Copy>("windowsExe") {
    group = "distribution"
    description = "Builds the Windows executable installer (MdHabits-<version>.exe)."
    dependsOn("packageExe")
    from(layout.buildDirectory.dir("compose/binaries/main/exe")) { include("*.exe") }
    into(layout.buildDirectory.dir("windows"))
}
