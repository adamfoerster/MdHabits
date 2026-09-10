package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.markdown.MdPrayerLogCodec
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdPrayerLogCodecTest {

    @Test
    fun parsesLinesWithAnySeparatorVariant() {
        val content = """
            ---
            type: prayer-log
            month: 2026-09
            ---

            - 2026-09-01 — 5/6
            - 2026-09-02 – 4/4
            - 2026-09-03 - 2/3
        """.trimIndent()

        val days = MdPrayerLogCodec.parseMonthLog(content)

        assertEquals(3, days.size)
        assertEquals(LocalDate(2026, 9, 1), days[0].date)
        assertEquals(5, days[0].prayed)
        assertEquals(6, days[0].total)
        assertFalse(days[0].isComplete)
        assertTrue(days[1].isComplete)
    }

    @Test
    fun lineWithoutAFractionSuffixParsesAsZeroOfZero() {
        val days = MdPrayerLogCodec.parseMonthLog("- 2026-09-05")

        assertEquals(1, days.size)
        assertEquals(0, days[0].prayed)
        assertEquals(0, days[0].total)
        assertFalse(days[0].isComplete)
    }

    @Test
    fun malformedLinesAreSkippedNotFatal() {
        val content = """
            - not a date — 1/1
            garbage line
            - 2026-13-40 — 1/1
        """.trimIndent()

        assertEquals(emptyList(), MdPrayerLogCodec.parseMonthLog(content))
    }

    @Test
    fun isCompleteRequiresPrayedEqualsTotalAndAtLeastOneScheduled() {
        assertTrue(MdPrayerLogCodec.parseMonthLog("- 2026-09-01 — 3/3").single().isComplete)
        assertFalse(MdPrayerLogCodec.parseMonthLog("- 2026-09-01 — 0/0").single().isComplete)
        assertFalse(MdPrayerLogCodec.parseMonthLog("- 2026-09-01 — 2/5").single().isComplete)
    }

    @Test
    fun monthFileNameFormatsAsYyyyDashMm() {
        assertEquals("2026-09.md", MdPrayerLogCodec.monthFileName(LocalDate(2026, 9, 15)))
        assertEquals("2026-01.md", MdPrayerLogCodec.monthFileName(LocalDate(2026, 1, 1)))
    }
}
