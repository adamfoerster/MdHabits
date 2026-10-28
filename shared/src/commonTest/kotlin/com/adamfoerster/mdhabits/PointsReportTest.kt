package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.PointsReport
import com.adamfoerster.mdhabits.data.markdown.WeekNote
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val NOW = "2026-07-02T12:00:00Z"

private fun entry(id: String, weekId: String, delta: Int, label: String = "Ler") = PointsEvent(
    id, Instant.parse("2026-06-01T12:00:00Z"), weekId, PointsSource.TASK, "t-1", label, delta,
)

class PointsReportTest {
    private val vault = FakeVaultFileSystem()
    private val settings = FakeAppSettings().apply { vaultDisplayName = "MdHabits" }
    private val weeks = fixedWeekCalculator(NOW)
    private val tasks = InMemoryTaskRepository()

    private fun report(info: FakeAppInfo = FakeAppInfo(version = "0.21.0")) = PointsReport(
        vault = vault,
        weekStore = MarkdownWeekStore(vault, weeks),
        tasks = tasks,
        settings = settings,
        appInfo = info,
        weekCalculator = weeks,
        clock = fixedClock(NOW),
        timeZone = TimeZone.UTC,
    )

    private fun seed(name: String, note: WeekNote) =
        vault.externalWrite("weeks", name, MarkdownCodecs.encodeWeekNote(note))

    @Test
    fun reportListsDeviceVersionAndDisplayedBalance() = runTest {
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))

        val text = report().build()

        assertTrue("app version: 0.21.0" in text)
        assertTrue("device: Test Phone (OS 1)" in text)
        assertTrue("vault name: MdHabits" in text)
        assertTrue("current week: 2026-W27" in text)
        assertTrue("**displayed balance (what Home shows): 5**" in text)
        assertTrue("| L-1 |" in text)
    }

    @Test
    fun reportFlagsAClosedWeekWhoseCheckpointDisagreesWithItsLedger() = runTest {
        // The checkpoint says 100 but the week's own entries only add up to 60.
        seed(
            "2026-W20.md",
            WeekNote(
                "2026-W20",
                closed = true,
                pointsAtWeekStart = 0,
                pointsAtWeekEnd = 100,
                events = listOf(entry("L-1", "2026-W20", 10), entry("L-2", "2026-W20", 50)),
            ),
        )
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-3", "2026-W27", 5))))

        val text = report().build()

        assertTrue("**displayed balance (what Home shows): 105**" in text)
        assertTrue("ignoring the checkpoints: 65" in text)
        assertTrue("difference (displayed - ledger sum): 40" in text)
        assertTrue("MISMATCH (start + events = 60)" in text)
        // The closed week's body is read from disk, so its entries still appear in the ledger.
        assertTrue("| L-1 |" in text && "| L-2 |" in text)
    }

    @Test
    fun reportShowsSyncConflictCopiesAndTheAppIgnoresThem() = runTest {
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))
        seed(
            "2026-W27.sync-conflict-20260702-120000-ABCDEFG.md",
            WeekNote("2026-W27", events = listOf(entry("L-9", "2026-W27", 900))),
        )

        val text = report().build()

        assertTrue("SYNC CONFLICT COPY" in text)
        assertTrue("**displayed balance (what Home shows): 5**" in text)
        assertTrue("| L-9 |" !in text, "a conflict copy's entries are not part of the ledger")
    }

    @Test
    fun identicalNotesGetTheSameHashAndDifferentOnesDont() = runTest {
        seed("2026-W26.md", WeekNote("2026-W26", events = listOf(entry("L-1", "2026-W26", 5))))
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))
        val first = report().build()
        val second = report().build()

        fun hashes(text: String) = text.lines().filter { it.startsWith("| 2026-W2") && ".md" in it }
            .map { it.split("|")[4].trim() }

        assertEquals(hashes(first), hashes(second))
        assertEquals(2, hashes(first).toSet().size)
    }

    @Test
    fun reportListsTheTasks() = runTest {
        tasks.upsert(Task(id = "t-1", title = "Ler | cap", points = 7))

        val text = report().build()

        assertTrue("| t-1 | Ler \\| cap | 7 |" in text)
    }

    @Test
    fun writeSavesTheReportInTheVaultNamedAfterTheDevice() = runTest {
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))

        val path = report(FakeAppInfo(device = "Google Pixel 8 (Android 15)")).write()

        assertEquals("diagnostics/points-report-20260702-120000-google-pixel-8-android-15.md", path)
        val saved = vault.files[path]
        assertTrue(saved != null && saved.startsWith("# MdHabits points report"))
    }

    @Test
    fun reportAndSaveMessageNameTheRealVaultLocation() = runTest {
        val located = object : VaultFileSystem by vault {
            override val rootDescription = "picked folder primary:Sync/MdHabits"
        }
        val report = PointsReport(
            located, MarkdownWeekStore(located, weeks), tasks, settings, FakeAppInfo(), weeks,
            fixedClock(NOW), TimeZone.UTC,
        )

        assertTrue("vault location: picked folder primary:Sync/MdHabits" in report.build())
        assertEquals("picked folder primary:Sync/MdHabits", report.location)
    }

    @Test
    fun writeFailsWhenTheVaultSilentlyDropsTheFile() = runTest {
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))
        val dropping = object : VaultFileSystem by vault {
            override suspend fun write(dir: String, name: String, content: String) {
                if (dir != "diagnostics") vault.write(dir, name, content)
            }
        }
        val report = PointsReport(
            dropping, MarkdownWeekStore(dropping, weeks), tasks, settings, FakeAppInfo(), weeks,
            fixedClock(NOW), TimeZone.UTC,
        )

        assertFailsWith<IllegalStateException> { report.write() }
    }

    @Test
    fun writingTheReportDoesNotChangeTheBalance() = runTest {
        seed("2026-W27.md", WeekNote("2026-W27", events = listOf(entry("L-1", "2026-W27", 5))))
        // The first load settles the note's checkpoints; after that a report must leave it alone.
        report().build()
        val before = vault.files["weeks/2026-W27.md"]

        report().write()

        assertEquals(before, vault.files["weeks/2026-W27.md"])
    }
}
