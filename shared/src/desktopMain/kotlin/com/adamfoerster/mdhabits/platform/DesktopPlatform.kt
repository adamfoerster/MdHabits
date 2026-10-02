package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.io.File
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * The version comes from the `mdhabits.version` system property, which the `desktopApp` module
 * sets from its own version, so the value shown in Settings matches the packaged build.
 */
class DesktopAppInfo : AppInfo {
    override val version: String
        get() = System.getProperty(VERSION_PROPERTY).orEmpty()

    companion object {
        const val VERSION_PROPERTY = "mdhabits.version"
    }
}

/** [AppSettings] backed by [java.util.prefs.Preferences] (registry on Windows, plist on macOS). */
class DesktopAppSettings(
    private val prefs: Preferences = Preferences.userRoot().node("com/adamfoerster/mdhabits"),
) : AppSettings {

    override var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.putBoolean(KEY_ONBOARDED, value)

    override var vaultDisplayName: String?
        get() = prefs.get(KEY_VAULT_NAME, null)
        set(value) = prefs.putOrRemove(KEY_VAULT_NAME, value)

    override var vaultRef: String?
        get() = prefs.get(KEY_VAULT_REF, null)
        set(value) = prefs.putOrRemove(KEY_VAULT_REF, value)

    override var languageTag: String?
        get() = prefs.get(KEY_LANG, null)
        set(value) = prefs.putOrRemove(KEY_LANG, value)

    override var mdPrayerEnabled: Boolean
        get() = prefs.getBoolean(KEY_MDPRAYER_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_MDPRAYER_ENABLED, value)

    override var mdPrayerFolderDisplayName: String?
        get() = prefs.get(KEY_MDPRAYER_NAME, null)
        set(value) = prefs.putOrRemove(KEY_MDPRAYER_NAME, value)

    override var mdPrayerFolderRef: String?
        get() = prefs.get(KEY_MDPRAYER_REF, null)
        set(value) = prefs.putOrRemove(KEY_MDPRAYER_REF, value)

    override var mdPrayerLinkedTaskId: String?
        get() = prefs.get(KEY_MDPRAYER_TASK, null)
        set(value) = prefs.putOrRemove(KEY_MDPRAYER_TASK, value)

    override var healthConnectEnabled: Boolean
        get() = prefs.getBoolean(KEY_HEALTH_ENABLED, false)
        set(value) = prefs.putBoolean(KEY_HEALTH_ENABLED, value)

    private fun Preferences.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else put(key, value)
    }

    private companion object {
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_VAULT_NAME = "vault_display_name"
        const val KEY_VAULT_REF = "vault_ref"
        const val KEY_LANG = "language_tag"
        const val KEY_MDPRAYER_ENABLED = "mdprayer_enabled"
        const val KEY_MDPRAYER_NAME = "mdprayer_folder_display_name"
        const val KEY_MDPRAYER_REF = "mdprayer_folder_ref"
        const val KEY_MDPRAYER_TASK = "mdprayer_linked_task_id"
        const val KEY_HEALTH_ENABLED = "health_connect_enabled"
    }
}

/** Shows the native folder chooser; the vault ref is simply the chosen folder's absolute path. */
class DesktopVaultPicker : VaultPicker {
    override suspend fun pickVault(): VaultSelection? = withContext(Dispatchers.Swing) {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        val folder: File = chooser.selectedFile ?: return@withContext null
        VaultSelection(displayName = folder.name.ifBlank { folder.path }, ref = folder.absolutePath)
    }
}
