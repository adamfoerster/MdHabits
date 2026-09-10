package com.adamfoerster.mdhabits.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import kotlinx.coroutines.CompletableDeferred

/**
 * Bridge between the Android [android.app.Activity] (which hosts the SAF folder picker and holds a
 * Context) and the platform-agnostic storage layer. [MainActivity] populates these.
 */
object AndroidVaultBridge {
    var appContext: Context? = null

    /** Set by MainActivity to launch ACTION_OPEN_DOCUMENT_TREE. */
    var launchTreePicker: (() -> Unit)? = null

    /** Awaited by [AndroidVaultPicker]; completed from the activity result callback. */
    var pending: CompletableDeferred<VaultSelection?>? = null

    /** Called by MainActivity when the user picks (or cancels) a tree Uri. */
    fun onTreePicked(uri: Uri?) {
        val selection = uri?.let {
            appContext?.contentResolver?.takePersistableUriPermission(
                it,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            VaultSelection(displayName = displayNameOf(it), ref = it.toString())
        }
        pending?.complete(selection)
        pending = null
    }

    private fun displayNameOf(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        val tail = docId?.substringAfterLast(':')?.substringAfterLast('/')
        return tail?.takeIf { it.isNotBlank() } ?: (treeUri.lastPathSegment ?: "Vault")
    }
}

class AndroidVaultPicker : VaultPicker {
    override suspend fun pickVault(): VaultSelection? {
        val deferred = CompletableDeferred<VaultSelection?>()
        AndroidVaultBridge.pending = deferred
        val launch = AndroidVaultBridge.launchTreePicker
        if (launch == null) {
            deferred.complete(null)
        } else {
            launch()
        }
        return deferred.await()
    }
}

class AndroidAppInfo(private val context: Context) : AppInfo {
    override val version: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
}

class AndroidAppSettings(context: Context) : AppSettings {
    private val prefs = context.getSharedPreferences("mdhabits", Context.MODE_PRIVATE)

    override var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    override var vaultDisplayName: String?
        get() = prefs.getString(KEY_VAULT_NAME, null)
        set(value) = prefs.edit().putString(KEY_VAULT_NAME, value).apply()

    override var vaultRef: String?
        get() = prefs.getString(KEY_VAULT_REF, null)
        set(value) = prefs.edit().putString(KEY_VAULT_REF, value).apply()

    override var languageTag: String?
        get() = prefs.getString(KEY_LANG, null)
        set(value) = prefs.edit().putString(KEY_LANG, value).apply()

    override var mdPrayerEnabled: Boolean
        get() = prefs.getBoolean(KEY_MDPRAYER_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MDPRAYER_ENABLED, value).apply()

    override var mdPrayerFolderDisplayName: String?
        get() = prefs.getString(KEY_MDPRAYER_NAME, null)
        set(value) = prefs.edit().putString(KEY_MDPRAYER_NAME, value).apply()

    override var mdPrayerFolderRef: String?
        get() = prefs.getString(KEY_MDPRAYER_REF, null)
        set(value) = prefs.edit().putString(KEY_MDPRAYER_REF, value).apply()

    override var mdPrayerLinkedTaskId: String?
        get() = prefs.getString(KEY_MDPRAYER_TASK, null)
        set(value) = prefs.edit().putString(KEY_MDPRAYER_TASK, value).apply()

    private companion object {
        const val KEY_ONBOARDED = "onboarding_complete"
        const val KEY_VAULT_NAME = "vault_display_name"
        const val KEY_VAULT_REF = "vault_ref"
        const val KEY_LANG = "language_tag"
        const val KEY_MDPRAYER_ENABLED = "mdprayer_enabled"
        const val KEY_MDPRAYER_NAME = "mdprayer_folder_display_name"
        const val KEY_MDPRAYER_REF = "mdprayer_folder_ref"
        const val KEY_MDPRAYER_TASK = "mdprayer_linked_task_id"
    }
}
