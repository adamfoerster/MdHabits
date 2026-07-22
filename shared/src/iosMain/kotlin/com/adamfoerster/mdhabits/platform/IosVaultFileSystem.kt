package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * iOS [VaultFileSystem] backed by NSFileManager. Uses [AppSettings.vaultRef] as the vault path
 * when the user has picked a folder (the ref is read on every call so a pick mid-session takes
 * effect immediately); until then it falls back to `Documents/MdHabits`, which the user can expose
 * in the Files app. Security-scoped bookmark support arrives with the real iOS folder picker.
 */
@OptIn(ExperimentalForeignApi::class)
class IosVaultFileSystem(private val settings: AppSettings) : VaultFileSystem {

    private fun root(): String =
        settings.vaultRef?.takeIf { it.startsWith("/") }
            ?: (documentsPath() + "/MdHabits")

    private fun documentsPath(): String =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: NSFileManager.defaultManager.currentDirectoryPath

    override suspend fun list(dir: String): List<String> = withContext(Dispatchers.IO) {
        val names = NSFileManager.defaultManager
            .contentsOfDirectoryAtPath("${root()}/$dir", null)
            .orEmpty()
        names.filterIsInstance<String>().filter { it.endsWith(".md") }
    }

    override suspend fun read(dir: String, name: String): String? = withContext(Dispatchers.IO) {
        NSString.stringWithContentsOfFile(
            "${root()}/$dir/$name", encoding = NSUTF8StringEncoding, error = null,
        )
    }

    override suspend fun write(dir: String, name: String, content: String): Unit =
        withContext(Dispatchers.IO) {
            val folder = "${root()}/$dir"
            NSFileManager.defaultManager.createDirectoryAtPath(
                folder,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
            @Suppress("CAST_NEVER_SUCCEEDS")
            (content as NSString).writeToFile(
                "$folder/$name",
                atomically = true,
                encoding = NSUTF8StringEncoding,
                error = null,
            )
        }

    override suspend fun delete(dir: String, name: String): Unit = withContext(Dispatchers.IO) {
        NSFileManager.defaultManager.removeItemAtPath("${root()}/$dir/$name", error = null)
    }

    override suspend fun listIn(ref: String, dir: String): List<String> = withContext(Dispatchers.IO) {
        if (!ref.startsWith("/")) return@withContext emptyList()
        NSFileManager.defaultManager
            .contentsOfDirectoryAtPath("$ref/$dir", null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.endsWith(".md") }
    }

    override suspend fun readIn(ref: String, dir: String, name: String): String? =
        withContext(Dispatchers.IO) {
            if (!ref.startsWith("/")) return@withContext null
            NSString.stringWithContentsOfFile(
                "$ref/$dir/$name", encoding = NSUTF8StringEncoding, error = null,
            )
        }
}
