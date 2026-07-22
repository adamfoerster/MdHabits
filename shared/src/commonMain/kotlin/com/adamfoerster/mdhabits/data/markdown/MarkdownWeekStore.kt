package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything the app stores for one ISO week, kept in a single `weeks/<weekId>.md` note. */
data class WeekNote(
    val weekId: String,
    val instances: List<TaskInstance> = emptyList(),
    val review: WeeklyReview? = null,
    val events: List<PointsEvent> = emptyList(),
)

/**
 * Owns the consolidated `weeks/<weekId>.md` notes. Task instances, the weekly review, and the
 * points ledger of a week all live in the same note, so the task, review, and ledger repositories
 * share this store (one Koin singleton) instead of writing overlapping files.
 *
 * The Home screen treats the mere existence of the current week's note as "the week was started":
 * it is created by the weekly review (planning the week), by onboarding, or by completing a task.
 *
 * Loading migrates pre-0.7.0 vaults: legacy `ledger/<weekId>.md` and `reviews/<weekId>.md` notes
 * are folded into the week notes and then deleted. A legacy note that fails to decode is left in
 * place and ignored, never fatal. Data already in the week note wins over the legacy files.
 */
class MarkdownWeekStore(private val vault: VaultFileSystem) {
    private val notes = MutableStateFlow<Map<String, WeekNote>>(emptyMap())
    private val loadGuard = Mutex()
    private var loaded = false

    suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        val byWeek = vault.list(WEEKS_DIR)
            .mapNotNull { name -> vault.read(WEEKS_DIR, name)?.let(MarkdownCodecs::decodeWeekNote) }
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

        notes.value = byWeek
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

    /** Applies [transform] to the week's note (creating it if absent) and writes it through. */
    suspend fun updateWeek(weekId: String, transform: (WeekNote) -> WeekNote) {
        ensureLoaded()
        val updated = transform(notes.value[weekId] ?: WeekNote(weekId))
        notes.update { it + (weekId to updated) }
        persist(updated)
    }

    private suspend fun persist(note: WeekNote) =
        vault.write(WEEKS_DIR, "${note.weekId}.md", MarkdownCodecs.encodeWeekNote(note))

    private companion object {
        const val WEEKS_DIR = "weeks"
        const val LEGACY_LEDGER_DIR = "ledger"
        const val LEGACY_REVIEWS_DIR = "reviews"
    }
}
