package com.adamfoerster.mdhabits.data.markdown

import kotlinx.datetime.LocalDate

/** One day's entry in an mdPrayer `log/<yyyy-MM>.md` note. */
data class MdPrayerDayStatus(val date: LocalDate, val prayed: Int, val total: Int) {
    /** mdPrayer's own rule for a fully-prayed day: every scheduled item was prayed. */
    val isComplete: Boolean get() = total > 0 && prayed == total
}

/**
 * Parses mdPrayer's monthly prayer log notes (see mdPrayer's `LogCodec.kt`/README): a `- YYYY-MM-DD`
 * line per day, optionally followed by ` — prayed/total` (the separator may be an em dash, en dash,
 * or hyphen). Lines that don't match are skipped rather than failing the whole file, matching this
 * project's "Markdown loading must stay lenient" rule.
 */
object MdPrayerLogCodec {
    private val LINE_REGEX = Regex("""^-\s*(\d{4}-\d{2}-\d{2})\s*(?:[—\-–]\s*(\d+)\s*/\s*(\d+))?""")

    fun parseMonthLog(content: String): List<MdPrayerDayStatus> = content.lineSequence()
        .mapNotNull { line -> LINE_REGEX.find(line.trim()) }
        .mapNotNull { match ->
            val date = runCatching { LocalDate.parse(match.groupValues[1]) }.getOrNull() ?: return@mapNotNull null
            val prayed = match.groupValues[2].toIntOrNull() ?: 0
            val total = match.groupValues[3].toIntOrNull() ?: 0
            MdPrayerDayStatus(date, prayed, total)
        }
        .toList()

    /** The log file name mdPrayer uses for the month containing [date], e.g. "2026-09.md". */
    fun monthFileName(date: LocalDate): String {
        val month = date.monthNumber.toString().padStart(2, '0')
        return "${date.year}-$month.md"
    }
}
