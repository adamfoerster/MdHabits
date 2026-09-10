package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.datetime.LocalDate

/**
 * Reads an mdPrayer vault folder through [VaultFileSystem]'s ref-based peek (`listIn`/`readIn`),
 * the same mechanism [VaultInspector] uses to look at a folder without adopting it — mdPrayer's
 * folder is never made this app's own vault.
 */
class MarkdownMdPrayerRepository(private val vault: VaultFileSystem) : MdPrayerRepository {

    override suspend fun looksLikeMdPrayerVault(ref: String): Boolean =
        vault.listIn(ref, LOG_DIR).any { name ->
            LOG_FILE_REGEX.matches(name) &&
                vault.readIn(ref, LOG_DIR, name)?.contains(PRAYER_LOG_MARKER) == true
        }

    override suspend fun completedDates(ref: String, range: WeekRange): Set<LocalDate> {
        val fileNames = setOf(
            MdPrayerLogCodec.monthFileName(range.start),
            MdPrayerLogCodec.monthFileName(range.endInclusive),
        )
        return fileNames
            .flatMap { name -> vault.readIn(ref, LOG_DIR, name)?.let(MdPrayerLogCodec::parseMonthLog).orEmpty() }
            .filter { it.isComplete && it.date >= range.start && it.date <= range.endInclusive }
            .map { it.date }
            .toSet()
    }

    private companion object {
        const val LOG_DIR = "log"
        const val PRAYER_LOG_MARKER = "type: prayer-log"
        val LOG_FILE_REGEX = Regex("""\d{4}-\d{2}\.md""")
    }
}
