package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.storage.NoteCacheStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.StandardCopyOption
import java.nio.file.Files

/** Keeps the note cache next to the app's fallback vault, in `~/.mdhabits` (never inside the vault). */
class DesktopNoteCacheStorage(
    private val file: File = File(System.getProperty("user.home"), ".mdhabits/note-cache.json"),
) : NoteCacheStorage {

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    override suspend fun save(content: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            file.parentFile?.mkdirs()
            // Through a temporary file, so a crash mid-save never leaves half a cache.
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(content)
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
