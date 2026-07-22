package com.adamfoerster.mdhabits.core.platform

/**
 * Platform-provided information about the installed app. Implementations read the version the
 * platform actually installed (Android `versionName`, iOS `CFBundleShortVersionString`), so the
 * value shown in Settings can never drift from the released build.
 */
interface AppInfo {
    /** The user-visible semver string, e.g. "0.8.0", or empty if the platform can't resolve it. */
    val version: String
}
