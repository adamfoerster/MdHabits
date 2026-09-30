package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Desktop [VaultFileSystem] over plain files. The vault root is the folder path in
 * [AppSettings.vaultRef], read on every call so a folder picked mid-session takes effect
 * immediately; before a vault is chosen it falls back to [fallbackRoot] so data is never lost.
 *
 * Like the other platforms, everything is best-effort: an unreadable folder degrades to an empty
 * list / null read instead of throwing.
 */
class DesktopVaultFileSystem(
    private val settings: AppSettings,
    private val fallbackRoot: File = File(System.getProperty("user.home"), ".mdhabits/vault"),
) : VaultFileSystem {

    private fun root(): File = settings.vaultRef?.let(::File) ?: fallbackRoot

    override suspend fun list(dir: String): List<String> = listAt(root(), dir)

    override suspend fun read(dir: String, name: String): String? = readAt(root(), dir, name)

    override suspend fun listIn(ref: String, dir: String): List<String> = listAt(File(ref), dir)

    override suspend fun readIn(ref: String, dir: String, name: String): String? =
        readAt(File(ref), dir, name)

    override suspend fun write(dir: String, name: String, content: String): Unit =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = File(root(), dir).apply { mkdirs() }
                File(folder, name).writeText(content)
            }
        }

    override suspend fun delete(dir: String, name: String): Unit = withContext(Dispatchers.IO) {
        runCatching { File(File(root(), dir), name).delete() }
    }

    private suspend fun listAt(root: File, dir: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { File(root, dir).listFiles()?.map { it.name }.orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(MD) }
    }

    private suspend fun readAt(root: File, dir: String, name: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { File(File(root, dir), name).takeIf { it.isFile }?.readText() }.getOrNull()
        }

    private companion object {
        const val MD = ".md"
    }
}
