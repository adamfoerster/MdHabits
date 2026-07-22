package com.adamfoerster.mdhabits.storage

import com.adamfoerster.mdhabits.core.settings.AppSettings

/**
 * Points the app at a newly picked vault folder without losing notes.
 *
 * [VaultFileSystem] implementations resolve [AppSettings.vaultRef] on every call, so the selection
 * must be persisted BEFORE anything is written — otherwise the notes land in the previous root
 * (the app-private fallback, or an earlier vault). [migrateTo] snapshots every note under the
 * current root, switches the settings to the new folder, then copies the snapshot over. Notes
 * already present in the new folder win: picking an existing vault never overwrites its content.
 */
class VaultMigrator(
    private val vault: VaultFileSystem,
    private val settings: AppSettings,
) {

    suspend fun migrateTo(selection: VaultSelection) {
        val snapshot = VAULT_DIRS.flatMap { dir ->
            vault.list(dir).mapNotNull { name ->
                vault.read(dir, name)?.let { content -> Note(dir, name, content) }
            }
        }
        settings.vaultDisplayName = selection.displayName
        settings.vaultRef = selection.ref
        snapshot.forEach { note ->
            if (vault.read(note.dir, note.name) == null) {
                vault.write(note.dir, note.name, note.content)
            }
        }
    }

    private data class Note(val dir: String, val name: String, val content: String)

    private companion object {
        /** Every folder of the vault layout (see data/markdown/MarkdownRepositories). */
        val VAULT_DIRS =
            listOf("values", "penalties", "rewards", "tasks", "theme", "weeks", "reviews", "ledger")
    }
}
