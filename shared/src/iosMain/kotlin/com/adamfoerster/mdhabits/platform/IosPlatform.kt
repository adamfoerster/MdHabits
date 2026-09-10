package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSBundle
import platform.Foundation.NSURL
import platform.Foundation.NSUserDefaults
import platform.Foundation.base64EncodedStringWithOptions
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.darwin.NSObject

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

    override var mdPrayerEnabled: Boolean
        get() = defaults.boolForKey(KEY_MDPRAYER_ENABLED)
        set(value) = defaults.setBool(value, KEY_MDPRAYER_ENABLED)

    override var mdPrayerFolderDisplayName: String?
        get() = defaults.stringForKey(KEY_MDPRAYER_NAME)
        set(value) = defaults.setObject(value, KEY_MDPRAYER_NAME)

    override var mdPrayerFolderRef: String?
        get() = defaults.stringForKey(KEY_MDPRAYER_REF)
        set(value) = defaults.setObject(value, KEY_MDPRAYER_REF)

    override var mdPrayerLinkedTaskId: String?
        get() = defaults.stringForKey(KEY_MDPRAYER_TASK)
        set(value) = defaults.setObject(value, KEY_MDPRAYER_TASK)

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

/**
 * Presents [UIDocumentPickerViewController] in folder-picking mode and turns the chosen URL into
 * a security-scoped bookmark (`"bookmark:<base64>"`), so [IosVaultFileSystem] can re-resolve
 * access to it later — including a folder outside this app's own sandbox, such as another app's
 * folder exposed through the Files app (e.g. mdPrayer's `Documents/Prayer`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosVaultPicker : VaultPicker {
    override suspend fun pickVault(): VaultSelection? {
        val deferred = CompletableDeferred<VaultSelection?>()
        val presenter = topViewController()
        if (presenter == null) {
            deferred.complete(null)
        } else {
            // Kotlin/Native can't mix an ObjC supertype (NSObject/the delegate protocol) with a
            // plain Kotlin interface (VaultPicker) on the same class, so the delegate lives here,
            // kept alive by this suspend function's coroutine frame until it completes [deferred].
            val delegate = DocumentPickerDelegate(deferred)
            val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder))
            picker.delegate = delegate
            picker.allowsMultipleSelection = false
            presenter.presentViewController(picker, animated = true, completion = null)
        }
        return deferred.await()
    }

    private fun topViewController(): UIViewController? {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
        while (true) {
            top = top.presentedViewController ?: return top
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class DocumentPickerDelegate(
    private val result: CompletableDeferred<VaultSelection?>,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        result.complete(url?.let(::bookmarkSelection))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        result.complete(null)
    }

    /** Encodes [url] as a security-scoped bookmark so access survives past this launch. */
    private fun bookmarkSelection(url: NSURL): VaultSelection? {
        val granted = url.startAccessingSecurityScopedResource()
        return try {
            val data = url.bookmarkDataWithOptions(
                options = 0uL,
                includingResourceValuesForKeys = null,
                relativeToURL = null,
                error = null,
            ) ?: return null
            VaultSelection(
                displayName = url.lastPathComponent ?: "Folder",
                ref = "bookmark:" + data.base64EncodedStringWithOptions(0uL),
            )
        } finally {
            if (granted) url.stopAccessingSecurityScopedResource()
        }
    }
}
