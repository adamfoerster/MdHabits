package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.storage.VaultFileSystem

/** In-memory [VaultFileSystem] for tests; contents survive across repository instances. */
class FakeVaultFileSystem : VaultFileSystem {
    val files = mutableMapOf<String, String>() // "dir/name" -> content

    override suspend fun list(dir: String): List<String> =
        files.keys.filter { it.startsWith("$dir/") }
            .map { it.substringAfter('/') }
            .filter { it.endsWith(".md") }
            .sorted()

    override suspend fun read(dir: String, name: String): String? = files["$dir/$name"]

    override suspend fun write(dir: String, name: String, content: String) {
        files["$dir/$name"] = content
    }

    override suspend fun delete(dir: String, name: String) {
        files.remove("$dir/$name")
    }

    // The fake has a single root, so ref-based peeks see the same files.
    override suspend fun listIn(ref: String, dir: String): List<String> = list(dir)

    override suspend fun readIn(ref: String, dir: String, name: String): String? = read(dir, name)
}
