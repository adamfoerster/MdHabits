package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.storage.NoteCacheStorage
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Keeps the note cache in `Library/Application Support/MdHabits` — app-private and hidden from
 * the Files app, unlike the `Documents/MdHabits` fallback vault.
 */
@OptIn(ExperimentalForeignApi::class)
class IosNoteCacheStorage : NoteCacheStorage {

    private fun folder(): String =
        (NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
            .firstOrNull() as? String ?: NSFileManager.defaultManager.currentDirectoryPath) + "/MdHabits"

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        NSString.stringWithContentsOfFile("${folder()}/$FILE", encoding = NSUTF8StringEncoding, error = null)
    }

    override suspend fun save(content: String): Unit = withContext(Dispatchers.IO) {
        val folder = folder()
        NSFileManager.defaultManager.createDirectoryAtPath(
            folder, withIntermediateDirectories = true, attributes = null, error = null,
        )
        // atomically = true writes a temporary file first, so a crash never leaves half a cache.
        @Suppress("CAST_NEVER_SUCCEEDS")
        (content as NSString).writeToFile("$folder/$FILE", atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private companion object {
        const val FILE = "note-cache.json"
    }
}
