package com.adamfoerster.mdhabits.storage

/**
 * Minimal file access to the user's vault folder, implemented per platform (Android SAF tree /
 * plain files; iOS NSFileManager). Paths are a single subdirectory level ([dir]) plus a file
 * [name], which is all the Markdown layout needs.
 *
 * Implementations must be resilient: a missing folder or revoked permission should surface as an
 * empty list / null read rather than an exception, so the app keeps working and re-syncs when
 * access returns.
 */
interface VaultFileSystem {
    /** Names of the `.md` files directly inside [dir], or empty if the folder doesn't exist. */
    suspend fun list(dir: String): List<String>

    /** The text content of `dir/name`, or null if it doesn't exist or can't be read. */
    suspend fun read(dir: String, name: String): String?

    /** Writes (creating or truncating) `dir/name`, creating [dir] if needed. */
    suspend fun write(dir: String, name: String, content: String)

    /** Deletes `dir/name` if it exists. */
    suspend fun delete(dir: String, name: String)

    /** Like [list], but against the folder referenced by [ref] instead of the persisted vault. */
    suspend fun listIn(ref: String, dir: String): List<String>

    /** Like [read], but against the folder referenced by [ref] instead of the persisted vault. */
    suspend fun readIn(ref: String, dir: String, name: String): String?
}
