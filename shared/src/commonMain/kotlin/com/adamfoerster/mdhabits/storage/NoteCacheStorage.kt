package com.adamfoerster.mdhabits.storage

/**
 * App-private storage for [CachedVaultFileSystem]'s snapshot of the vault notes: one opaque text
 * blob kept outside the vault (it must never sync to other devices), so the next launch starts
 * from the notes as they were last seen and only re-reads the files that changed since.
 *
 * Best-effort like [VaultFileSystem]: a missing or unreadable snapshot loads as null, a failed
 * save is ignored — losing the cache only costs one full read of the vault.
 */
interface NoteCacheStorage {
    suspend fun load(): String?
    suspend fun save(content: String)
}

/** No persisted cache: every launch reads the whole vault (tests, or a platform without storage). */
object NoNoteCacheStorage : NoteCacheStorage {
    override suspend fun load(): String? = null
    override suspend fun save(content: String) = Unit
}
