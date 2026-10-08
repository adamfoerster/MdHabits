package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Everything the app stores for one ISO week, kept in a single `weeks/<weekId>.md` note.
 *
 * [pointsAtWeekStart] is the balance as the week began. Once the week is [closed] its
 * [pointsAtWeekEnd] is fixed and becomes the checkpoint later balances build on; from then on the
 * note's body (instances, health, ledger, journal) is no longer loaded, so a closed note in memory
 * carries only its frontmatter — see [MarkdownWeekStore.fullNote] for the history.
 */
data class WeekNote(
    val weekId: String,
    val instances: List<TaskInstance> = emptyList(),
    val review: WeeklyReview? = null,
    val events: List<PointsEvent> = emptyList(),
    /** The daily values the health sync read, one entry per day; empty without the integration. */
    val health: List<DailyHealth> = emptyList(),
    val closed: Boolean = false,
    val pointsAtWeekStart: Int? = null,
    /** Set only on a [closed] week. */
    val pointsAtWeekEnd: Int? = null,
)

/** The balance at the start and end of a week. */
data class WeekPoints(val start: Int, val end: Int)

/**
 * Running balances across the week notes, oldest week first. A closed week contributes its
 * checkpoint ([WeekNote.pointsAtWeekEnd]) instead of its events — its body isn't loaded — so the
 * ledger only ever has to be summed over the weeks still open.
 */
fun weekPoints(notes: Map<String, WeekNote>): Map<String, WeekPoints> {
    var running = 0
    // weekIds sort chronologically.
    return notes.keys.sorted().associateWith { weekId ->
        val note = notes.getValue(weekId)
        val end = note.pointsAtWeekEnd
        val points = if (note.closed && end != null) {
            WeekPoints(note.pointsAtWeekStart ?: running, end)
        } else {
            WeekPoints(running, running + note.events.sumOf { it.delta })
        }
        running = points.end
        points
    }
}

/** The current balance: the end of the latest week (0 with no weeks at all). */
fun balanceOf(notes: Map<String, WeekNote>): Int = weekPoints(notes).values.lastOrNull()?.end ?: 0

/**
 * Owns the consolidated `weeks/<weekId>.md` notes. Task instances, the weekly review, the health
 * values and the points ledger of a week all live in the same note, so the task, review, health
 * and ledger repositories share this store (one Koin singleton) instead of writing overlapping files.
 *
 * Besides loading and writing notes, the store keeps the points checkpoints settled ([settle]):
 * every open week's `pointsAtWeekStart` follows the weeks before it, and a week is closed — its
 * `pointsAtWeekEnd` computed and fixed — once more than [CLOSE_AFTER_DAYS] days have passed since
 * it ended. That runs on load, after every change, and on every poll of the [VaultWatcher], so a
 * week closes even when nothing is edited. A closed week is read-only: [updateWeek] ignores it.
 *
 * Loading migrates pre-0.7.0 vaults: legacy `ledger/<weekId>.md` and `reviews/<weekId>.md` notes
 * are folded into the week notes and then deleted. A legacy note that fails to decode is left in
 * place and ignored, never fatal. Data already in the week note wins over the legacy files.
 */
class MarkdownWeekStore(
    private val vault: VaultFileSystem,
    private val weekCalculator: WeekCalculator = WeekCalculator(),
    /** The vault folder's path inside the Obsidian vault, prefixed to the links in the notes. */
    private val linkRoot: () -> String? = { null },
) : VaultRefreshable {
    private val notes = MutableStateFlow<Map<String, WeekNote>>(emptyMap())
    private val loadGuard = Mutex()
    private var loaded = false
    // Last-modified stamp of every week note as loaded, so [refreshFromVault] re-reads only the
    // notes another device (via Syncthing) or Obsidian changed — however old the week is.
    private val stamps = FolderStamps(vault, WEEKS_DIR)
    // Serializes read-modify-write updates with refreshes.
    private val ioGuard = Mutex()

    suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        val byWeek = stamps.changes()?.changed.orEmpty().decodeWeekNotes()
            .associateBy { it.weekId }
            .toMutableMap()

        val migrated = mutableSetOf<String>()
        vault.list(LEGACY_LEDGER_DIR).forEach { name ->
            val events = vault.read(LEGACY_LEDGER_DIR, name)?.let(MarkdownCodecs::decodeLedgerWeek)
                ?: return@forEach
            val weekId = events.firstOrNull()?.weekId ?: name.removeSuffix(".md")
            val note = byWeek[weekId] ?: WeekNote(weekId)
            val known = note.events.mapTo(mutableSetOf()) { it.id }
            byWeek[weekId] = note.copy(events = note.events + events.filter { it.id !in known })
            migrated += weekId
            vault.delete(LEGACY_LEDGER_DIR, name)
        }
        vault.list(LEGACY_REVIEWS_DIR).forEach { name ->
            val review = vault.read(LEGACY_REVIEWS_DIR, name)?.let(MarkdownCodecs::decodeReview)
                ?: return@forEach
            val note = byWeek[review.weekId] ?: WeekNote(review.weekId)
            if (note.review == null) byWeek[review.weekId] = note.copy(review = review)
            migrated += review.weekId
            vault.delete(LEGACY_REVIEWS_DIR, name)
        }
        migrated.forEach { weekId -> byWeek[weekId]?.let { persist(it) } }

        commit(before = byWeek, after = byWeek)
        loaded = true
    }

    fun observeNotes(): Flow<Map<String, WeekNote>> = flow {
        ensureLoaded()
        emitAll(notes)
    }

    suspend fun snapshot(): Map<String, WeekNote> {
        ensureLoaded()
        return notes.value
    }

    suspend fun isClosed(weekId: String): Boolean = snapshot()[weekId]?.closed == true

    /**
     * The week's note with its whole body. For an open week that is the note in memory; a closed
     * week's history isn't kept loaded, so its file is read and decoded in full on demand.
     */
    suspend fun fullNote(weekId: String): WeekNote? {
        val note = snapshot()[weekId] ?: return null
        if (!note.closed) return note
        return vault.read(WEEKS_DIR, "$weekId.md")
            ?.let { MarkdownCodecs.decodeWeekNote(it, includeClosedBody = true) }
            ?: note
    }

    /**
     * Applies [transform] to the week's note (creating it if absent) and writes it through, along
     * with any later week whose `pointsAtWeekStart` moved. A closed week is never changed: its body
     * isn't loaded, so writing it back would erase its history.
     */
    suspend fun updateWeek(weekId: String, transform: (WeekNote) -> WeekNote) {
        ensureLoaded()
        ioGuard.withLock {
            val current = notes.value
            val note = current[weekId] ?: WeekNote(weekId)
            if (note.closed) return@withLock
            commit(before = current, after = current + (weekId to transform(note)))
        }
    }

    /**
     * Re-reads the week notes whose file changed since they were loaded, drops the ones whose file
     * is gone, and settles the checkpoints (which also closes weeks as time passes). A changed note
     * that no longer decodes keeps its last good value in memory.
     */
    override suspend fun refreshFromVault() = ioGuard.withLock {
        if (!loaded) return@withLock
        val current = notes.value
        val changes = stamps.changes()?.takeUnless { it.isEmpty }
        val refreshed = if (changes == null) {
            current
        } else {
            current - changes.removed.map { it.noteKey() }.toSet() +
                changes.changed.decodeWeekNotes().associateBy { it.weekId }
        }
        commit(before = current, after = refreshed)
    }

    /** Settles [after], publishes it, and writes every note that differs from [before]. */
    private suspend fun commit(before: Map<String, WeekNote>, after: Map<String, WeekNote>) {
        val settled = settle(after)
        notes.value = settled
        settled.values.filter { it != before[it.weekId] }.forEach { persist(it) }
    }

    /**
     * Every open week gets the running balance as its `pointsAtWeekStart`; one that ended more than
     * [CLOSE_AFTER_DAYS] days ago is closed with its `pointsAtWeekEnd`. Closed weeks are left as
     * they are — their checkpoint is what the following weeks build on.
     */
    private fun settle(notes: Map<String, WeekNote>): Map<String, WeekNote> {
        val today = weekCalculator.today()
        val points = weekPoints(notes)
        return notes.mapValues { (weekId, note) ->
            if (note.closed) return@mapValues note
            val span = points.getValue(weekId)
            val opened = note.copy(pointsAtWeekStart = span.start, pointsAtWeekEnd = null)
            if (isPastClosing(weekId, today)) opened.copy(closed = true, pointsAtWeekEnd = span.end) else opened
        }
    }

    private fun isPastClosing(weekId: String, today: LocalDate): Boolean {
        val end = weekCalculator.rangeOfWeekId(weekId)?.endInclusive ?: return false
        return today > end.plus(CLOSE_AFTER_DAYS, DateTimeUnit.DAY)
    }

    /**
     * Decodes `<weekId>.md` notes, skipping any copy whose file name isn't its own week (a
     * duplicated or sync-tool copy of a week note would otherwise override the real one).
     */
    private fun Map<String, String>.decodeWeekNotes(): List<WeekNote> = mapNotNull { (name, text) ->
        MarkdownCodecs.decodeWeekNote(text)?.takeIf { it.weekId == name.noteKey() }
    }

    private suspend fun persist(note: WeekNote) {
        vault.write(WEEKS_DIR, "${note.weekId}.md", MarkdownCodecs.encodeWeekNote(note, linkRoot()))
        stamps.written("${note.weekId}.md")
    }

    companion object {
        /** A week closes once more than this many days have passed since its Sunday. */
        const val CLOSE_AFTER_DAYS = 7

        private const val WEEKS_DIR = "weeks"
        private const val LEGACY_LEDGER_DIR = "ledger"
        private const val LEGACY_REVIEWS_DIR = "reviews"
    }
}
