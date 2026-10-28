package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.displayLabel
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds a Markdown diagnostic of everything that goes into the points balance, to compare two
 * devices that show different totals over the same synced vault: the app and device, the balance
 * as displayed, each week's checkpoints (in memory and as stored in the file), a fingerprint of
 * every note, sync-conflict copies, and the full ledger.
 *
 * It only reads. [write] saves the report as `diagnostics/points-report-<time>-<device>.md` in the
 * vault; the file name carries the device, so reports from two devices never overwrite each other.
 */
class PointsReport(
    private val vault: VaultFileSystem,
    private val weekStore: MarkdownWeekStore,
    private val tasks: TaskRepository,
    private val settings: AppSettings,
    private val appInfo: AppInfo,
    private val weekCalculator: WeekCalculator,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    /**
     * Writes the report into the vault and returns its path relative to the vault root. The vault
     * implementations swallow write errors, so the file is read back: a report that isn't there
     * throws instead of being reported as saved.
     */
    suspend fun write(): String {
        val now = clock.now()
        val name = "points-report-${stamp(now)}-${slug(appInfo.device)}.md"
        val content = build(now)
        vault.write(DIAGNOSTICS_DIR, name, content)
        check(vault.read(DIAGNOSTICS_DIR, name) == content) { "The report wasn't written to the vault" }
        return "$DIAGNOSTICS_DIR/$name"
    }

    suspend fun build(now: Instant = clock.now()): String {
        val memory = weekStore.snapshot()
        val points = weekPoints(memory)
        val files = readWeekFiles()
        // The notes that really are a week's note: file named after its own week id.
        val canonical = files.filter { it.note != null && it.name == "${it.note.weekId}.md" }
        val ledger = canonical.flatMap { it.note!!.events }.sortedBy { it.timestamp }
        val displayed = balanceOf(memory)
        val ignoringCheckpoints = ledger.sumOf { it.delta }

        return buildString {
            appendLine("# MdHabits points report")
            appendLine()
            appendLine("## Device")
            appendLine()
            appendLine("- generated: ${iso(now)} (UTC), local ${local(now)} ${timeZone.id}")
            appendLine("- app version: ${appInfo.version.ifEmpty { "?" }}")
            appendLine("- device: ${appInfo.device.ifEmpty { "?" }}")
            appendLine("- vault name: ${settings.vaultDisplayName ?: "-"}")
            appendLine("- vault ref: ${settings.vaultRef ?: "-"}")
            appendLine("- today: ${weekCalculator.today()}, current week: ${weekCalculator.weekId()}")
            appendLine("- mdPrayer sync: ${settings.mdPrayerEnabled}, health sync: ${settings.healthConnectEnabled}")
            appendLine()
            appendLine("## Balance")
            appendLine()
            appendLine("- **displayed balance (what Home shows): $displayed**")
            appendLine("- sum of every ledger entry in the week files, ignoring the checkpoints: $ignoringCheckpoints")
            appendLine("- difference (displayed - ledger sum): ${displayed - ignoringCheckpoints}")
            appendLine()
            appendLine("## Weeks in memory")
            appendLine()
            appendLine("How the balance is derived: a closed week contributes its stored end checkpoint, an open one")
            appendLine("its start plus the sum of its events.")
            appendLine()
            appendLine("| week | closed | start | end | stored start | stored end | events | events sum |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
            memory.keys.sorted().forEach { weekId ->
                val note = memory.getValue(weekId)
                val span = points.getValue(weekId)
                appendLine(
                    "| $weekId | ${note.closed} | ${span.start} | ${span.end} | ${note.pointsAtWeekStart ?: "-"} " +
                        "| ${note.pointsAtWeekEnd ?: "-"} | ${note.events.size} | ${note.events.sumOf { it.delta }} |",
                )
            }
            appendLine()
            appendLine("(events are empty for a closed week: its body isn't kept in memory — see the next section.)")
            appendLine()
            appendLine("## Week files on disk")
            appendLine()
            appendLine("`hash` fingerprints the whole file text: the same hash on both devices means identical content.")
            appendLine("`check` compares a closed week's stored end with its start plus the events in its body.")
            appendLine()
            if (files.isEmpty()) {
                appendLine("No readable `weeks/` folder.")
            } else {
                appendLine("| file | modified | chars | hash | closed | start | end | events | events sum | check | note |")
                appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
                files.forEach { appendLine(it.row()) }
            }
            appendLine()
            appendLine("## Other folders")
            appendLine()
            appendLine("| folder | notes | newest modification |")
            appendLine("| --- | --- | --- |")
            INVENTORY_DIRS.forEach { dir ->
                val listing = vault.listModified(dir)
                val conflicts = listing?.keys?.count { it.isSyncConflictCopy() } ?: 0
                appendLine(
                    "| $dir | ${listing?.size?.toString() ?: "-"}" +
                        "${if (conflicts > 0) " ($conflicts sync-conflict)" else ""} " +
                        "| ${listing?.values?.maxOrNull()?.let { iso(Instant.fromEpochMilliseconds(it)) } ?: "-"} |",
                )
            }
            appendLine()
            appendLine("## Tasks")
            appendLine()
            appendLine("| id | title | points | recurrence | active | habit since | done on |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            tasks.observeTasks(activeOnly = false).first().sortedBy { it.id }.forEach { task ->
                appendLine(
                    "| ${task.id} | ${cell(task.title)} | ${task.points} | ${task.recurrence} | ${task.active} " +
                        "| ${task.habitSince ?: "-"} | ${task.doneOn ?: "-"} |",
                )
            }
            appendLine()
            appendLine("## Ledger (${ledger.size} entries, oldest first)")
            appendLine()
            appendLine("| timestamp | week | source | delta | label | ref | id |")
            appendLine("| --- | --- | --- | --- | --- | --- | --- |")
            ledger.forEach { appendLine(it.row()) }
        }
    }

    private suspend fun readWeekFiles(): List<WeekFile> {
        val listing = vault.listModified(WEEKS_DIR) ?: return emptyList()
        return listing.keys.sorted().map { name ->
            val text = vault.read(WEEKS_DIR, name)
            val doc = text?.let(Frontmatter::parse)
            WeekFile(
                name = name,
                modified = listing.getValue(name),
                chars = text?.length,
                hash = text?.let(::fingerprint),
                rawClosed = doc?.boolean("closed"),
                rawStart = doc?.int("pointsAtWeekStart"),
                rawEnd = doc?.int("pointsAtWeekEnd"),
                note = text?.let { MarkdownCodecs.decodeWeekNote(it, includeClosedBody = true) },
            )
        }
    }

    private inner class WeekFile(
        val name: String,
        val modified: Long,
        val chars: Int?,
        val hash: String?,
        val rawClosed: Boolean?,
        val rawStart: Int?,
        val rawEnd: Int?,
        val note: WeekNote?,
    ) {
        fun row(): String {
            val events = note?.events.orEmpty()
            val sum = events.sumOf { it.delta }
            val notes = buildList {
                if (name.isSyncConflictCopy()) add("SYNC CONFLICT COPY (ignored by the app)")
                if (note == null) add("unreadable or not a week note (ignored by the app)")
                else if (name != "${note.weekId}.md" && !name.isSyncConflictCopy()) add("file name differs from week ${note.weekId} (ignored)")
            }
            val check = when {
                note == null || rawStart == null -> "-"
                rawEnd == null -> "open"
                rawStart + sum == rawEnd -> "ok"
                else -> "MISMATCH (start + events = ${rawStart + sum})"
            }
            return "| $name | ${iso(Instant.fromEpochMilliseconds(modified))} | ${chars ?: "-"} | ${hash ?: "-"} " +
                "| ${rawClosed ?: "-"} | ${rawStart ?: "-"} | ${rawEnd ?: "-"} | ${events.size} | $sum " +
                "| $check | ${notes.joinToString("; ").ifEmpty { "-" }} |"
        }
    }

    private fun PointsEvent.row(): String =
        "| ${iso(timestamp)} | $weekId | $source | $delta | ${cell(displayLabel)} | ${cell(refId)} | ${cell(id)} |"

    private fun cell(text: String): String = text.replace("|", "\\|").replace('\n', ' ')

    private fun iso(at: Instant): String = at.toString()

    private fun local(at: Instant): String =
        at.toLocalDateTime(timeZone).toString()

    /** `yyyyMMdd-HHmmss` in local time, for the file name. */
    private fun stamp(at: Instant): String {
        val t = at.toLocalDateTime(timeZone)
        fun two(n: Int) = n.toString().padStart(2, '0')
        return "${t.year}${two(t.monthNumber)}${two(t.dayOfMonth)}-${two(t.hour)}${two(t.minute)}${two(t.second)}"
    }

    private fun slug(device: String): String =
        device.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifEmpty { "device" }

    /** 64-bit FNV-1a of the text, in hex: enough to tell two copies of a note apart. */
    private fun fingerprint(text: String): String {
        var hash = 0xcbf29ce484222325uL.toLong()
        text.forEach { char ->
            hash = (hash xor char.code.toLong()) * 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }

    companion object {
        const val DIAGNOSTICS_DIR = "diagnostics"
        private const val WEEKS_DIR = "weeks"
        private val INVENTORY_DIRS = listOf("weeks", "tasks", "theme", "values", "penalties", "rewards", "ledger", "reviews")
    }
}
