package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.VaultWatcher
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.completing
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Notes changed outside the app (another device's edits arriving through Syncthing) must reach the
 * in-memory state while the app is open, keyed on each file's last-modified stamp.
 */
class VaultWatcherTest {

    private val vault = FakeVaultFileSystem()
    private val date = LocalDate(2026, 10, 7)

    private fun weekWithCompletion(weekId: String, taskId: String) = WeekNote(
        weekId = weekId,
        instances = listOf(TaskInstance(taskId, weekId, planned = true).completing(true, date)),
    )

    @Test
    fun completionSyncedFromAnotherDeviceIsPickedUp() = runTest {
        vault.externalWrite(
            "weeks", "2026-W41.md",
            MarkdownCodecs.encodeWeekNote(WeekNote("2026-W41", listOf(TaskInstance("t-1", "2026-W41", planned = true)))),
        )
        val store = MarkdownWeekStore(vault, fixedWeekCalculator())
        val repo = MarkdownTaskRepository(vault, store)
        assertFalse(repo.observeInstances("2026-W41").first().single().completed)

        vault.externalWrite("weeks", "2026-W41.md", MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W41", "t-1")))
        store.refreshFromVault()

        assertTrue(repo.observeInstances("2026-W41").first().single().completed)
        assertEquals(setOf("t-1"), repo.observeCompletedTaskIds().first())
    }

    @Test
    fun syncConflictCopyArrivingWithTheUpdateDoesNotOverrideIt() = runTest {
        val stale = MarkdownCodecs.encodeWeekNote(
            WeekNote("2026-W41", listOf(TaskInstance("t-1", "2026-W41", planned = true))),
        )
        vault.externalWrite("weeks", "2026-W41.md", stale)
        val store = MarkdownWeekStore(vault, fixedWeekCalculator())
        store.ensureLoaded()

        // Syncthing resolves a conflict in one go: the other device's note wins, the local one is
        // kept beside it as a conflict copy holding the same week.
        vault.externalWrite("weeks", "2026-W41.md", MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W41", "t-1")))
        vault.externalWrite("weeks", "2026-W41.sync-conflict-20261007-101500-ABCDEFG.md", stale)
        store.refreshFromVault()

        assertTrue(store.snapshot().getValue("2026-W41").instances.single().completed)
    }

    @Test
    fun syncConflictCopyIsIgnoredOnLoad() = runTest {
        vault.externalWrite("weeks", "2026-W41.md", MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W41", "t-1")))
        vault.externalWrite(
            "weeks", "2026-W41.sync-conflict-20261007-101500-ABCDEFG.md",
            MarkdownCodecs.encodeWeekNote(WeekNote("2026-W41")),
        )
        vault.externalWrite("values", "v-1.sync-conflict-20261007-101500-ABCDEFG.md", MarkdownCodecs.encodeValue(PersonalValue("v-1", "Velho")))
        vault.externalWrite("values", "v-1.md", MarkdownCodecs.encodeValue(PersonalValue("v-1", "Foco")))

        assertTrue(MarkdownWeekStore(vault, fixedWeekCalculator()).snapshot().getValue("2026-W41").instances.single().completed)
        assertEquals(listOf("Foco"), MarkdownValueRepository(vault).observeValues().first().map { it.name })
    }

    @Test
    fun aWeekNoteUnderAnotherFileNameIsIgnored() = runTest {
        vault.externalWrite("weeks", "2026-W41.md", MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W41", "t-1")))
        vault.externalWrite("weeks", "2026-W41 copy.md", MarkdownCodecs.encodeWeekNote(WeekNote("2026-W41")))

        assertTrue(MarkdownWeekStore(vault, fixedWeekCalculator()).snapshot().getValue("2026-W41").instances.single().completed)
    }

    @Test
    fun anOldWeekChangedWithAnOlderStampIsStillReloaded() = runTest {
        // Syncthing keeps the source device's mtime, so a synced edit can be stamped *earlier*.
        vault.externalWrite("weeks", "2026-W01.md", MarkdownCodecs.encodeWeekNote(WeekNote("2026-W01")), at = 5_000)
        val store = MarkdownWeekStore(vault, fixedWeekCalculator())
        assertTrue(store.snapshot().getValue("2026-W01").instances.isEmpty())

        vault.externalWrite("weeks", "2026-W01.md", MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W01", "t-9")), at = 10)
        store.refreshFromVault()

        assertEquals("t-9", store.snapshot().getValue("2026-W01").instances.single().taskId)
    }

    @Test
    fun unchangedStampsSkipReading() = runTest {
        // Already settled (pointsAtWeekStart in place), so loading it writes nothing back.
        vault.externalWrite("weeks", "2026-W41.md", MarkdownCodecs.encodeWeekNote(WeekNote("2026-W41", pointsAtWeekStart = 0)))
        val store = MarkdownWeekStore(vault, fixedWeekCalculator())
        store.ensureLoaded()

        // Same stamp, different content: not re-read (the stamp is what signals a change).
        vault.files["weeks/2026-W41.md"] = MarkdownCodecs.encodeWeekNote(weekWithCompletion("2026-W41", "t-1"))
        store.refreshFromVault()

        assertTrue(store.snapshot().getValue("2026-W41").instances.isEmpty())
    }

    @Test
    fun newAndDeletedNotesAreReflected() = runTest {
        val values = MarkdownValueRepository(vault)
        values.upsert(PersonalValue("v-1", "Foco"))

        vault.externalWrite("values", "v-2.md", MarkdownCodecs.encodeValue(PersonalValue("v-2", "Calma")))
        vault.externalDelete("values", "v-1.md")
        values.refreshFromVault()

        assertEquals(listOf("v-2"), values.observeValues().first().map { it.id })
    }

    @Test
    fun unreachableVaultDoesNotWipeLoadedData() = runTest {
        val values = MarkdownValueRepository(vault)
        values.upsert(PersonalValue("v-1", "Foco"))

        vault.unreachable = true
        values.refreshFromVault()

        assertEquals(listOf("v-1"), values.observeValues().first().map { it.id })
    }

    @Test
    fun brokenEditKeepsTheLastGoodValue() = runTest {
        val themes = MarkdownThemeRepository(vault)
        themes.upsertTheme(AnnualTheme(id = "th-1", year = 2026, name = "Ano da Saúde"))

        vault.externalWrite("theme", "2026.md", "not a note")
        themes.refreshFromVault()
        assertEquals("Ano da Saúde", themes.getTheme(2026)?.name)

        vault.externalWrite("theme", "2026.md", MarkdownCodecs.encodeTheme(AnnualTheme(id = "th-1", year = 2026, name = "Ano do Foco")))
        themes.refreshFromVault()
        assertEquals("Ano do Foco", themes.getTheme(2026)?.name)
    }

    @Test
    fun refreshBeforeFirstLoadIsANoOp() = runTest {
        vault.externalWrite("values", "v-1.md", MarkdownCodecs.encodeValue(PersonalValue("v-1", "Foco")))
        val values = MarkdownValueRepository(vault)
        values.refreshFromVault()

        assertEquals(listOf("v-1"), values.observeValues().first().map { it.id })
    }

    @Test
    fun watcherPollsWhileRunning() = runTest {
        val themes = MarkdownThemeRepository(vault)
        assertNull(themes.getTheme(2026))
        val watcher = VaultWatcher(listOf(themes), interval = 3.seconds)

        val job = launch { watcher.watch() }
        runCurrent()
        vault.externalWrite("theme", "2026.md", MarkdownCodecs.encodeTheme(AnnualTheme(id = "th-1", year = 2026, name = "Ano do Foco")))
        advanceTimeBy(3.seconds + 1.seconds / 1000)

        assertEquals("Ano do Foco", themes.getTheme(2026)?.name)
        job.cancel()
    }
}
