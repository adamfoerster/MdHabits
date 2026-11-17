package com.adamfoerster.mdhabits.platform

import android.content.Context
import com.adamfoerster.mdhabits.storage.NoteCacheStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Keeps the note cache in the app's private files (never inside the synced vault folder). */
class AndroidNoteCacheStorage(context: Context) : NoteCacheStorage {
    private val file = File(context.filesDir, "note-cache.json")

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    override suspend fun save(content: String): Unit = withContext(Dispatchers.IO) {
        runCatching { file.writeAtomically(content) }
    }
}

/** Writes through a temporary file and renames it, so a crash mid-save never leaves half a cache. */
internal fun File.writeAtomically(content: String) {
    parentFile?.mkdirs()
    val tmp = File(parentFile, "$name.tmp")
    tmp.writeText(content)
    if (!tmp.renameTo(this)) {
        delete()
        tmp.renameTo(this)
    }
}
