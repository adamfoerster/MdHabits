package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults

class IosAppInfo : AppInfo {
    override val version: String
        get() = (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            .orEmpty()
}

class IosAppSettings : AppSettings {
    private val defaults = NSUserDefaults.standardUserDefaults

    override var onboardingComplete: Boolean
        get() = defaults.boolForKey(KEY_ONBOARDED)
        set(value) = defaults.setBool(value, KEY_ONBOARDED)

    override var vaultDisplayName: String?
        get() = defaults.stringForKey(KEY_VAULT_NAME)
        set(value) = defaults.setObject(value, KEY_VAULT_NAME)

    override var vaultRef: String?
        get() = defaults.stringForKey(KEY_VAULT_REF)
        set(value) = defaults.setObject(value, KEY_VAULT_REF)

    override var languageTag: String?
        get() = defaults.stringForKey(KEY_LANG)
        set(value) = defaults.setObject(value, KEY_LANG)

    private companion object {
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_VAULT_NAME = "vault_display_name"
        const val KEY_VAULT_REF = "vault_ref"
        const val KEY_LANG = "language_tag"
    }
}

/**
 * Placeholder picker for the MVP. The real UIDocumentPickerViewController + security-scoped
 * bookmark implementation arrives in Phase 6; for now it returns null (folder step is optional).
 */
class IosVaultPicker : VaultPicker {
    override suspend fun pickVault(): VaultSelection? = null
}
