package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.storage.CachedVaultFileSystem
import com.adamfoerster.mdhabits.storage.NoteCacheStorage
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CachedVaultFileSystemTest {

    /** [FakeVaultFileSystem] that counts the reads reaching the "disk". */
    private class CountingVault(val fake: FakeVaultFileSystem = FakeVaultFileSystem()) : VaultFileSystem by fake {
        val reads = mutableListOf<String>()
        override suspend fun read(dir: String, name: String): String? {
            reads += "$dir/$name"
            return fake.read(dir, name)
        }
    }

    private class MemoryCacheStorage : NoteCacheStorage {
        var saved: String? = null
        override suspend fun load() = saved
        override suspend fun save(content: String) { saved = content }
    }

    /**
     * Lets the background writer and the debounced save run. [backgroundScope] work is skipped by
     * `advanceUntilIdle`, so time is advanced explicitly.
     */
    private fun TestScope.settle() {
        advanceTimeBy(5.seconds)
        runCurrent()
    }

    private fun TestScope.cached(
        vault: VaultFileSystem,
        storage: NoteCacheStorage = MemoryCacheStorage(),
        ref: () -> String? = { "vault-a" },
        scope: CoroutineScope = backgroundScope,
    ) = CachedVaultFileSystem(vault, storage, ref, scope)

    @Test
    fun writeReturnsBeforeTheFileIsWrittenAndIsReadableMeanwhile() = runTest {
        val disk = CountingVault()
        val vault = cached(disk)

        vault.write("values", "v1.md", "hello")

        assertNull(disk.fake.files["values/v1.md"], "the file is written in the background")
        assertEquals(1, vault.pendingCount())
        assertEquals("hello", vault.read("values", "v1.md"))
        assertEquals(listOf("v1.md"), vault.list("values"))
        disk.fake.externalWrite("values", "other.md", "o", at = 10)
        assertEquals(
            mapOf("other.md" to 10L, "v1.md" to VaultFileSystem.PENDING_STAMP),
            vault.listModified("values"),
        )

        settle()
        assertEquals("hello", disk.fake.files["values/v1.md"])
        assertEquals(0, vault.pendingCount())
    }

    @Test
    fun anUnreachableFolderStaysUnreachableDespitePendingWrites() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "a.md", "a", at = 10)
        disk.fake.unreachable = true
        val vault = cached(disk)

        vault.write("values", "b.md", "b")

        // Null, not just the pending note: otherwise every other note would pass for deleted.
        assertNull(vault.listModified("values"))
    }

    @Test
    fun repeatedWritesToOneNoteCollapseIntoTheLast() = runTest {
        val disk = CountingVault()
        val vault = cached(disk)

        vault.write("values", "v1.md", "one")
        vault.write("values", "v1.md", "two")
        vault.write("values", "v1.md", "three")
        settle()

        assertEquals("three", disk.fake.files["values/v1.md"])
    }

    @Test
    fun pendingDeleteHidesTheNoteUntilApplied() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "v1.md", "x", at = 10)
        val vault = cached(disk)

        vault.delete("values", "v1.md")

        assertNull(vault.read("values", "v1.md"))
        assertTrue(vault.list("values").isEmpty())
        assertTrue(vault.listModified("values").orEmpty().isEmpty())
        assertEquals("x", disk.fake.files["values/v1.md"])
        settle()
        assertNull(disk.fake.files["values/v1.md"])
    }

    @Test
    fun unchangedNotesAreServedFromMemory() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "v1.md", "x", at = 10)
        val vault = cached(disk)

        vault.listModified("values")
        vault.read("values", "v1.md")
        vault.listModified("values")
        assertEquals("x", vault.read("values", "v1.md"))
        assertEquals(1, disk.reads.size)

        // Edited by another app: the stamp differs, so the new version is read.
        disk.fake.externalWrite("values", "v1.md", "y", at = 5)
        vault.listModified("values")
        assertEquals("y", vault.read("values", "v1.md"))
        assertEquals(2, disk.reads.size)
    }

    @Test
    fun aRestartReadsOnlyTheNotesModifiedSinceTheSavedCopy() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "a.md", "a", at = 10)
        disk.fake.externalWrite("values", "b.md", "b", at = 11)
        val storage = MemoryCacheStorage()

        val first = cached(disk, storage)
        first.listModified("values")
        first.read("values", "a.md")
        first.read("values", "b.md")
        settle() // the copy is saved after a short delay
        assertTrue(storage.saved != null)

        // While the app is closed another device edits b.
        disk.fake.externalWrite("values", "b.md", "b2", at = 20)
        disk.reads.clear()

        val second = cached(disk, storage)
        second.listModified("values")
        assertEquals("a", second.read("values", "a.md"))
        assertEquals("b2", second.read("values", "b.md"))
        assertEquals(listOf("values/b.md"), disk.reads)
    }

    @Test
    fun aCopyOfAnotherVaultFolderIsNotUsed() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "a.md", "a", at = 10)
        val storage = MemoryCacheStorage()
        var ref = "vault-a"

        val first = cached(disk, storage, ref = { ref })
        first.listModified("values")
        first.read("values", "a.md")
        first.flush()
        disk.reads.clear()

        ref = "vault-b"
        val second = cached(disk, storage, ref = { ref })
        second.listModified("values")
        second.read("values", "a.md")
        assertEquals(listOf("values/a.md"), disk.reads)
    }

    @Test
    fun noteWithoutAModificationTimeIsAlwaysReadFromDisk() = runTest {
        val disk = CountingVault()
        disk.fake.externalWrite("values", "a.md", "a", at = 0)
        val vault = cached(disk)

        repeat(2) {
            vault.listModified("values")
            vault.read("values", "a.md")
        }
        assertEquals(2, disk.reads.size)
    }

    @Test
    fun flushAppliesEveryPendingChange() = runTest {
        val disk = CountingVault()
        val vault = cached(disk)

        vault.write("values", "a.md", "a")
        vault.write("tasks", "t.md", "t")
        vault.flush()

        assertEquals("a", disk.fake.files["values/a.md"])
        assertEquals("t", disk.fake.files["tasks/t.md"])
        assertEquals(0, vault.pendingCount())
    }

    @Test
    fun aRefreshWhileTheWriteIsPendingKeepsTheNewValue() = runTest {
        val disk = CountingVault()
        val vault = cached(disk)
        val repo = MarkdownValueRepository(vault)
        repo.upsert(PersonalValue(id = "v1", name = "Old"))
        settle()

        repo.upsert(PersonalValue(id = "v1", name = "New"))
        // The poll runs before the background write reaches the file.
        repo.refreshFromVault()
        assertEquals("New", repo.observeValues().first().single().name)
        assertFalse(disk.fake.files["values/v1.md"].orEmpty().contains("New"))

        settle()
        repo.refreshFromVault()
        assertEquals("New", repo.observeValues().first().single().name)
        assertTrue(disk.fake.files.getValue("values/v1.md").contains("New"))
    }

    @Test
    fun anEditByAnotherAppIsPickedUpThroughTheCache() = runTest {
        val disk = CountingVault()
        val vault = cached(disk)
        val repo = MarkdownValueRepository(vault)
        repo.upsert(PersonalValue(id = "v1", name = "Mine"))
        settle()
        repo.refreshFromVault()

        val edited = disk.fake.files.getValue("values/v1.md").replace("Mine", "Theirs")
        disk.fake.externalWrite("values", "v1.md", edited)
        repo.refreshFromVault()

        assertEquals("Theirs", repo.observeValues().first().single().name)
        runCurrent()
    }
}
