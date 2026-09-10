package com.adamfoerster.mdhabits.core.settings

/**
 * Small key-value app settings backed by the platform (SharedPreferences on Android, NSUserDefaults
 * on iOS). Kept synchronous and simple; the onboarding gate reads these once at startup.
 */
interface AppSettings {
    var onboardingComplete: Boolean

    /** Human-readable label of the chosen vault folder, shown in Settings. */
    var vaultDisplayName: String?

    /** Platform reference to the vault (Android tree Uri string / iOS bookmark or path). */
    var vaultRef: String?

    /** BCP-47 language tag chosen by the user, or null to follow the system language. */
    var languageTag: String?

    /** Whether the optional mdPrayer sync is turned on. */
    var mdPrayerEnabled: Boolean

    /** Human-readable label of the chosen mdPrayer folder, shown in Settings. */
    var mdPrayerFolderDisplayName: String?

    /** Platform reference to the mdPrayer folder (Android tree Uri string / iOS bookmark). */
    var mdPrayerFolderRef: String?

    /** Id of the [com.adamfoerster.mdhabits.domain.model.Task] mdPrayer completions are applied to. */
    var mdPrayerLinkedTaskId: String?
}
