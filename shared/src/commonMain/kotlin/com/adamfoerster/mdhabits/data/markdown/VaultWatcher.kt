package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Holds vault notes in memory and can re-sync them with the files currently on disk. */
interface VaultRefreshable {
    /** Re-reads every note whose file changed (or appeared/disappeared) since it was last loaded. */
    suspend fun refreshFromVault()
}

/**
 * Keeps the in-memory repositories in step with edits made outside the app — another device's
 * changes arriving through Syncthing, or a note edited in Obsidian. While the app is open (see
 * `App`), [watch] polls every [interval]: polling the files' last-modified stamps is the one
 * mechanism that works on every platform, including Android's Storage Access Framework, which has
 * no change notifications for a picked folder.
 */
class VaultWatcher(
    private val sources: List<VaultRefreshable>,
    private val interval: Duration = 3.seconds,
) {
    suspend fun refreshAll() = sources.forEach { it.refreshFromVault() }

    /** Refreshes right away (catching up on whatever synced while the app was away), then polls. */
    suspend fun watch(): Nothing {
        while (true) {
            refreshAll()
            delay(interval)
        }
    }
}

/** What changed in a vault folder since its stamps were last recorded. */
internal class FolderChanges(
    /** File name -> current content, for notes that are new or whose stamp changed. */
    val changed: Map<String, String>,
    /** File names that no longer exist. */
    val removed: Set<String>,
) {
    val isEmpty get() = changed.isEmpty() && removed.isEmpty()
}

/**
 * The last-modified stamp of every note of one vault folder, as the app last loaded it. A note is
 * re-read whenever its stamp *differs* from the recorded one — not only when it is newer — because
 * Syncthing carries the source device's modification time over, so an edit synced from another
 * device can legitimately carry an older stamp than the app's own last write.
 */
internal class FolderStamps(private val vault: VaultFileSystem, private val dir: String) {
    private val stamps = mutableMapOf<String, Long>()

    /**
     * Notes changed since the last call (on the first call: every note), recording their new
     * stamps; null when the folder can't be listed right now, so nothing is treated as deleted.
     * A note that can't be read is left unrecorded and retried on the next call.
     */
    suspend fun changes(): FolderChanges? {
        val listing = vault.listModified(dir)?.filterKeys { !it.isSyncConflictCopy() } ?: return null
        val removed = stamps.keys - listing.keys
        removed.forEach { stamps.remove(it) }
        val changed = buildMap {
            listing.forEach { (name, at) ->
                if (stamps[name] == at) return@forEach
                val text = vault.read(dir, name) ?: return@forEach
                stamps[name] = at
                put(name, text)
            }
        }
        return FolderChanges(changed, removed)
    }

    /**
     * Records that the app itself wrote [name]. Its real stamp isn't known yet, so the next
     * [changes] re-reads it once (getting back what was written); what matters is that the note
     * is now tracked, so a later deletion on another device is noticed.
     */
    fun written(name: String) {
        stamps[name] = UNKNOWN
    }

    /** Records that the app itself deleted [name]. */
    fun deleted(name: String) {
        stamps.remove(name)
    }

    private companion object {
        const val UNKNOWN = -1L
    }
}

internal fun String.noteKey(): String = removeSuffix(".md")

/**
 * Syncthing keeps the losing side of a conflict next to the note as
 * `<name>.sync-conflict-<date>-<time>-<device>.md`. It holds the same entity (same week/id) with
 * the stale content, so loading it would randomly override the real note: it is never read.
 */
internal fun String.isSyncConflictCopy(): Boolean = ".sync-conflict-" in this
