package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.storage.VaultFileSystem

/** In-memory [VaultFileSystem] for tests; contents survive across repository instances. */
class FakeVaultFileSystem : VaultFileSystem {
    val files = mutableMapOf<String, String>() // "dir/name" -> content
    val modified = mutableMapOf<String, Long>() // "dir/name" -> last-modified stamp
    private val dirs = mutableSetOf<String>()
    private var clock = 1_000L

    /** Simulates the folder becoming unreachable (e.g. a revoked permission): listings fail. */
    var unreachable = false

    /** Simulates a sync tool (Syncthing) writing a note behind the app's back, stamped [at]. */
    fun externalWrite(dir: String, name: String, content: String, at: Long = ++clock) {
        dirs += dir
        files["$dir/$name"] = content
        modified["$dir/$name"] = at
    }

    /** Simulates a sync tool deleting a note behind the app's back. */
    fun externalDelete(dir: String, name: String) {
        files.remove("$dir/$name")
        modified.remove("$dir/$name")
    }

    override suspend fun list(dir: String): List<String> =
        files.keys.filter { it.startsWith("$dir/") }
            .map { it.substringAfter('/') }
            .filter { it.endsWith(".md") }
            .sorted()

    override suspend fun listModified(dir: String): Map<String, Long>? {
        if (unreachable || (dir !in dirs && list(dir).isEmpty())) return null
        return list(dir).associateWith { modified["$dir/$it"] ?: 0L }
    }

    override suspend fun read(dir: String, name: String): String? = files["$dir/$name"]

    override suspend fun write(dir: String, name: String, content: String) {
        externalWrite(dir, name, content)
    }

    override suspend fun delete(dir: String, name: String) {
        externalDelete(dir, name)
    }

    // The fake has a single root, so ref-based peeks see the same files.
    override suspend fun listIn(ref: String, dir: String): List<String> = list(dir)

    override suspend fun readIn(ref: String, dir: String, name: String): String? = read(dir, name)
}
