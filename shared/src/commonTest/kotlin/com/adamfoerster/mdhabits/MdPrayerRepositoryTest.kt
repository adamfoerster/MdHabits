package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.data.markdown.MarkdownMdPrayerRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdPrayerRepositoryTest {

    private fun mdPrayerVault(vararg monthFiles: Pair<String, String>): FakeVaultFileSystem =
        FakeVaultFileSystem().apply {
            monthFiles.forEach { (name, content) -> files["log/$name"] = content }
        }

    private val validLog = """
        ---
        type: prayer-log
        month: 2026-09
        ---

        - 2026-09-01 — 5/5
        - 2026-09-02 — 3/5
    """.trimIndent()

    @Test
    fun looksLikeMdPrayerVaultIsTrueWhenAPrayerLogNoteExists() = runTest {
        val vault = mdPrayerVault("2026-09.md" to validLog)
        val repo = MarkdownMdPrayerRepository(vault)

        assertTrue(repo.looksLikeMdPrayerVault("some-ref"))
    }

    @Test
    fun looksLikeMdPrayerVaultIsFalseWithoutALogFolder() = runTest {
        val vault = FakeVaultFileSystem().apply { files["theme/2026.md"] = "not a prayer vault" }
        val repo = MarkdownMdPrayerRepository(vault)

        assertFalse(repo.looksLikeMdPrayerVault("some-ref"))
    }

    @Test
    fun looksLikeMdPrayerVaultIsFalseWhenLogFileLacksTheTypeMarker() = runTest {
        val vault = mdPrayerVault("2026-09.md" to "- 2026-09-01 — 1/1")
        val repo = MarkdownMdPrayerRepository(vault)

        assertFalse(repo.looksLikeMdPrayerVault("some-ref"))
    }

    @Test
    fun completedDatesSpansAWeekThatCrossesTwoMonths() = runTest {
        // 2026-W36 is Mon 2026-08-31 .. Sun 2026-09-06 — straddles August and September.
        val vault = mdPrayerVault(
            "2026-08.md" to """
                ---
                type: prayer-log
                ---
                - 2026-08-31 — 4/4
            """.trimIndent(),
            "2026-09.md" to """
                ---
                type: prayer-log
                ---
                - 2026-09-01 — 5/5
                - 2026-09-02 — 2/5
            """.trimIndent(),
        )
        val repo = MarkdownMdPrayerRepository(vault)
        val weekCalculator = WeekCalculator(fixedClock("2026-09-02T10:00:00Z"), TimeZone.UTC)

        val completed = repo.completedDates("some-ref", weekCalculator.rangeOf())

        assertEquals(setOf(LocalDate(2026, 8, 31), LocalDate(2026, 9, 1)), completed)
    }
}
