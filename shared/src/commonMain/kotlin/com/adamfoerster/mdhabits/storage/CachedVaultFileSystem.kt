package com.adamfoerster.mdhabits.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The vault as the app works with it: an in-memory copy of every note, in front of the platform
 * [VaultFileSystem] ([delegate]) that touches the real files.
 *
 * - **Reads** are served from memory whenever the note's last-modified stamp (from the latest
 *   [listModified]) still matches the one its content was read with; only notes that changed are
 *   read from disk. The copy is saved to [storage] (app-private, debounced by [saveDelay]), so on
 *   the next launch only the files modified since are read again — on Android's Storage Access
 *   Framework a read costs far more than a listing.
 * - **Writes and deletes** update memory and are queued as pending; a background coroutine in
 *   [scope] applies them to the files, so a caller never waits on file I/O. Until a note's pending
 *   change is applied, reads return the pending content and [listModified] reports it with
 *   [VaultFileSystem.PENDING_STAMP], so a refresh never re-reads the stale file over it. Several
 *   writes to one note before the queue reaches it collapse into the last one.
 *
 * The copy belongs to one vault folder ([vaultRef], read on every call like the platform file
 * systems do): when the folder changes the copy is dropped. Pending changes are not persisted, so
 * [flush] them before switching folders ([VaultMigrator] does).
 */
class CachedVaultFileSystem(
    private val delegate: VaultFileSystem,
    private val storage: NoteCacheStorage,
    private val vaultRef: () -> String?,
    scope: CoroutineScope,
    private val saveDelay: Duration = 1.seconds,
    private val retryDelay: Duration = 2.seconds,
) : VaultFileSystem {

    private sealed interface Pending {
        data class Write(val content: String) : Pending
        data object Delete : Pending
    }

    @Serializable
    private data class CachedNote(val stamp: Long, val content: String)

    @Serializable
    private data class Snapshot(val ref: String, val notes: Map<String, CachedNote>)

    /** Guards every field below; never held across delegate I/O. */
    private val lock = Mutex()
    /** Serializes applying the pending queue (the background loop and [flush]). */
    private val flushLock = Mutex()
    private var restored = false
    private var cacheRef: String? = null
    /** "dir/name" -> the note's content as last read from disk, with the stamp it had then. */
    private val cached = mutableMapOf<String, CachedNote>()
    /** "dir/name" -> stamp seen by the latest [listModified] of its folder. */
    private val listed = mutableMapOf<String, Long>()
    /** "dir/name" -> change not yet applied to the files, oldest first. */
    private val pending = LinkedHashMap<String, Pending>()
    private var snapshotDirty = false

    private val writeSignal = Channel<Unit>(Channel.CONFLATED)
    private val saveSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in writeSignal) flushLock.withLock { applyPending() }
        }
        scope.launch {
            for (signal in saveSignal) {
                delay(saveDelay)
                saveSnapshot()
            }
        }
    }

    override val rootDescription: String get() = delegate.rootDescription

    /** Number of writes/deletes still waiting to reach the files (diagnostics and tests). */
    suspend fun pendingCount(): Int = locked { pending.size }

    override suspend fun list(dir: String): List<String> {
        val names = delegate.list(dir)
        return locked {
            val inDir = pending.filterKeys { it.dirOf() == dir }
            val deleted = inDir.filterValues { it is Pending.Delete }.keys.map { it.nameOf() }.toSet()
            val written = inDir.filterValues { it is Pending.Write }.keys.map { it.nameOf() }
            (names - deleted + written).distinct().filter { it.endsWith(MD) }.sorted()
        }
    }

    override suspend fun listModified(dir: String): Map<String, Long>? {
        val listing = delegate.listModified(dir) ?: return null
        return locked {
            val prefix = "$dir/"
            listed.keys.removeAll { it.startsWith(prefix) && it.nameOf() !in listing }
            listing.forEach { (name, stamp) -> listed[prefix + name] = stamp }
            // Forget the content of notes deleted outside the app.
            if (cached.keys.removeAll { it.startsWith(prefix) && it.nameOf() !in listing }) markDirty()
            buildMap {
                putAll(listing)
                pending.forEach { (path, change) ->
                    if (path.dirOf() != dir) return@forEach
                    when (change) {
                        is Pending.Write -> put(path.nameOf(), VaultFileSystem.PENDING_STAMP)
                        Pending.Delete -> remove(path.nameOf())
                    }
                }
            }
        }
    }

    override suspend fun read(dir: String, name: String): String? {
        val path = "$dir/$name"
        val known = locked {
            when (val change = pending[path]) {
                is Pending.Write -> return@locked Known(change.content)
                Pending.Delete -> return@locked Known(null)
                null -> Unit
            }
            val note = cached[path]
            val stamp = listed[path]
            if (note != null && stamp != null && note.stamp == stamp) Known(note.content) else null
        }
        if (known != null) return known.content

        val content = delegate.read(dir, name)
        locked {
            val stamp = listed[path]
            // A provider that reports no modification time (0) can't tell changes apart: such a
            // note is never served from the copy, so an external edit can't hide behind it.
            if (content != null && stamp != null && stamp > 0 && path !in pending) {
                cached[path] = CachedNote(stamp, content)
                markDirty()
            }
        }
        return content
    }

    override suspend fun write(dir: String, name: String, content: String) = enqueue("$dir/$name", Pending.Write(content))

    override suspend fun delete(dir: String, name: String) = enqueue("$dir/$name", Pending.Delete)

    override suspend fun listIn(ref: String, dir: String): List<String> = delegate.listIn(ref, dir)

    override suspend fun readIn(ref: String, dir: String, name: String): String? = delegate.readIn(ref, dir, name)

    /** Applies every pending change now and saves the copy, returning once both are done. */
    override suspend fun flush() {
        flushLock.withLock { applyPending(retry = false) }
        saveSnapshot()
    }

    private suspend fun enqueue(path: String, change: Pending) {
        locked {
            // A note re-written while queued keeps its place in the queue, with the newest content.
            pending[path] = change
            if (cached.remove(path) != null) markDirty()
            if (change == Pending.Delete) listed.remove(path)
        }
        writeSignal.trySend(Unit)
    }

    /**
     * Applies the queue oldest first. A change that fails is kept and retried after [retryDelay]
     * (unless [retry] is false: then [flush] returns with it still queued for the background loop).
     */
    private suspend fun applyPending(retry: Boolean = true) {
        while (true) {
            val (path, change) = locked { pending.entries.firstOrNull()?.toPair() } ?: return
            val ok = try {
                when (change) {
                    is Pending.Write -> delegate.write(path.dirOf(), path.nameOf(), change.content)
                    Pending.Delete -> delegate.delete(path.dirOf(), path.nameOf())
                }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                if (!retry) return
                delay(retryDelay)
                continue
            }
            // Only drop it if it wasn't replaced by a newer change while being written.
            locked { if (pending[path] == change) pending.remove(path) }
        }
    }

    private suspend fun saveSnapshot() {
        val text = locked {
            if (!snapshotDirty) return@locked null
            snapshotDirty = false
            json.encodeToString(Snapshot.serializer(), Snapshot(cacheRef.orEmpty(), cached.toMap()))
        } ?: return
        try {
            storage.save(text)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Losing the snapshot only costs a full read of the vault on the next launch.
        }
    }

    private fun markDirty() {
        snapshotDirty = true
        saveSignal.trySend(Unit)
    }

    /** Runs [block] under [lock], after restoring the saved copy and checking the vault folder. */
    private suspend fun <T> locked(block: () -> T): T = lock.withLock {
        val ref = vaultRef().orEmpty()
        if (!restored) {
            restored = true
            cacheRef = ref
            val snapshot = try {
                storage.load()?.let { json.decodeFromString(Snapshot.serializer(), it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // An unreadable snapshot is just a cold start.
            }
            if (snapshot != null && snapshot.ref == ref) cached.putAll(snapshot.notes)
        }
        if (ref != cacheRef) {
            // Another vault folder: what was cached describes files that aren't there.
            cacheRef = ref
            cached.clear()
            listed.clear()
            markDirty()
        }
        block()
    }

    private class Known(val content: String?)

    private fun String.dirOf() = substringBefore('/')
    private fun String.nameOf() = substringAfter('/')

    private companion object {
        const val MD = ".md"
        val json = Json { ignoreUnknownKeys = true }
    }
}
