package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReport
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import com.adamfoerster.mdhabits.domain.model.completing
import com.adamfoerster.mdhabits.domain.model.mergedWith
import com.adamfoerster.mdhabits.domain.repository.HealthLogRepository
import com.adamfoerster.mdhabits.domain.repository.PenaltyRepository
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.repository.ValueRepository
import com.adamfoerster.mdhabits.domain.repository.WeeklyReviewRepository
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/**
 * Markdown-backed repositories. Each keeps an in-memory [MutableStateFlow] as the working state
 * (so observers behave exactly like the in-memory MVP repos) and syncs it with the vault: the
 * state is lazily loaded from the Markdown notes on first use, and every mutation writes the
 * affected note back through [VaultFileSystem]. Notes that fail to decode (e.g. broken by a manual
 * edit) are skipped on load, never fatal.
 *
 * Vault layout: `values/`, `penalties/`, `rewards/`, `tasks/` (one note per entity, `<id>.md`),
 * `theme/<year>.md`, and one consolidated `weeks/<weekId>.md` per week — task instances, weekly
 * review, and points ledger together, owned by the shared [MarkdownWeekStore].
 */

/**
 * Shared load-once + write-through plumbing for the simple `<id>.md`-per-entity folders, plus
 * [refreshFromVault] to pick up notes changed outside the app (a note that no longer decodes keeps
 * its last good value in memory).
 */
abstract class MarkdownCrudRepository<T>(
    private val vault: VaultFileSystem,
    private val dir: String,
) : VaultRefreshable {
    protected val items = MutableStateFlow<List<T>>(emptyList())
    private val loadGuard = Mutex()
    private var loaded = false
    private val stamps = FolderStamps(vault, dir)
    // Serializes writes with refreshes, so a refresh never reads a note the app is mid-way writing.
    private val ioGuard = Mutex()

    protected abstract fun idOf(item: T): String
    protected abstract fun encode(item: T): String
    protected abstract fun decode(text: String): T?

    protected suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        items.value = stamps.changes()?.changed.orEmpty().values.mapNotNull(::decode)
        loaded = true
    }

    override suspend fun refreshFromVault() = ioGuard.withLock {
        if (!loaded) return@withLock
        val changes = stamps.changes()?.takeUnless { it.isEmpty } ?: return@withLock
        val removedIds = changes.removed.mapTo(mutableSetOf()) { it.noteKey() }
        val decoded = changes.changed.values.mapNotNull(::decode)
        items.update { list ->
            decoded.fold(list.filterNot { idOf(it) in removedIds }) { acc, item -> acc.upserting(item) }
        }
    }

    private fun List<T>.upserting(item: T): List<T> =
        if (any { idOf(it) == idOf(item) }) map { if (idOf(it) == idOf(item)) item else it } else this + item

    protected fun observeAll(): Flow<List<T>> = flow {
        ensureLoaded()
        emitAll(items)
    }

    protected suspend fun getById(id: String): T? {
        ensureLoaded()
        return items.value.find { idOf(it) == id }
    }

    protected suspend fun save(item: T) {
        ensureLoaded()
        ioGuard.withLock {
            items.update { it.upserting(item) }
            vault.write(dir, "${idOf(item)}.md", encode(item))
            stamps.written("${idOf(item)}.md")
        }
    }

    protected suspend fun remove(id: String) {
        ensureLoaded()
        ioGuard.withLock {
            items.update { list -> list.filterNot { idOf(it) == id } }
            vault.delete(dir, "$id.md")
            stamps.deleted("$id.md")
        }
    }
}

class MarkdownValueRepository(vault: VaultFileSystem) :
    MarkdownCrudRepository<PersonalValue>(vault, DIR), ValueRepository {
    override fun idOf(item: PersonalValue) = item.id
    override fun encode(item: PersonalValue) = MarkdownCodecs.encodeValue(item)
    override fun decode(text: String) = MarkdownCodecs.decodeValue(text)

    override fun observeValues(): Flow<List<PersonalValue>> = observeAll()
    override suspend fun getValue(id: String): PersonalValue? = getById(id)
    override suspend fun upsert(value: PersonalValue) = save(value)
    override suspend fun delete(id: String) = remove(id)

    private companion object { const val DIR = "values" }
}

class MarkdownPenaltyRepository(vault: VaultFileSystem) :
    MarkdownCrudRepository<Penalty>(vault, DIR), PenaltyRepository {
    override fun idOf(item: Penalty) = item.id
    override fun encode(item: Penalty) = MarkdownCodecs.encodePenalty(item)
    override fun decode(text: String) = MarkdownCodecs.decodePenalty(text)

    override fun observePenalties(): Flow<List<Penalty>> = observeAll()
    override suspend fun getPenalty(id: String): Penalty? = getById(id)
    override suspend fun upsert(penalty: Penalty) = save(penalty)
    override suspend fun delete(id: String) = remove(id)

    private companion object { const val DIR = "penalties" }
}

class MarkdownRewardRepository(vault: VaultFileSystem) :
    MarkdownCrudRepository<Reward>(vault, DIR), RewardRepository {
    override fun idOf(item: Reward) = item.id
    override fun encode(item: Reward) = MarkdownCodecs.encodeReward(item)
    override fun decode(text: String) = MarkdownCodecs.decodeReward(text)

    override fun observeRewards(): Flow<List<Reward>> = observeAll()
    override suspend fun getReward(id: String): Reward? = getById(id)
    override suspend fun upsert(reward: Reward) = save(reward)
    override suspend fun delete(id: String) = remove(id)

    private companion object { const val DIR = "rewards" }
}

class MarkdownTaskRepository(
    vault: VaultFileSystem,
    private val weeks: MarkdownWeekStore,
) : MarkdownCrudRepository<Task>(vault, DIR), TaskRepository {

    override fun idOf(item: Task) = item.id
    override fun encode(item: Task) = MarkdownCodecs.encodeTask(item)
    override fun decode(text: String) = MarkdownCodecs.decodeTask(text)

    override fun observeTasks(activeOnly: Boolean): Flow<List<Task>> =
        observeAll().map { list -> if (activeOnly) list.filter { it.active } else list }

    override suspend fun getTask(id: String): Task? = getById(id)
    override suspend fun upsert(task: Task) = save(task)
    override suspend fun delete(id: String) = remove(id)

    override fun observeInstances(weekId: String): Flow<List<TaskInstance>> =
        weeks.observeNotes().map { it[weekId]?.instances.orEmpty() }

    // Planned/completed work or a review is what starts a week, not the note existing: a habit
    // charge (PenalizeMissedHabitsUseCase) writes ledger entries into weeks the user never opened,
    // and that must not pass for a started week and hide Home's weekly-review call to action.
    override fun observeWeekStarted(weekId: String): Flow<Boolean> =
        weeks.observeNotes().map { notes ->
            notes[weekId]?.let { it.instances.isNotEmpty() || it.review != null } == true
        }

    // Ad-hoc tasks are done for good once checked off. Their completion is read from the open
    // weeks and from the task's own `done_on` stamp, which outlives the week note being closed.
    override fun observeCompletedTaskIds(): Flow<Set<String>> =
        combine(weeks.observeNotes(), observeAll()) { notes, tasks ->
            notes.values.flatMap { it.instances }.filter { it.completed }.mapTo(mutableSetOf()) { it.taskId } +
                tasks.filter { it.doneOn != null }.map { it.id }
        }

    override suspend fun setPlanned(weekId: String, taskIds: List<String>) {
        val titles = titlesOf(taskIds)
        weeks.updateWeek(weekId) { note ->
            val existing = note.instances.associateBy { it.taskId }
            val updated = taskIds.map { id ->
                (existing[id]?.copy(planned = true) ?: TaskInstance(id, weekId, planned = true))
                    .withTitle(titles[id])
            }
            // Keep completed instances for tasks no longer planned so history is preserved.
            val keptCompleted = existing.values.filter { it.taskId !in taskIds && it.completed }
                .map { it.copy(planned = false) }
            note.copy(instances = updated + keptCompleted)
        }
    }

    override suspend fun setCompleted(taskId: String, weekId: String, completed: Boolean, on: LocalDate) {
        val task = getById(taskId)
        weeks.updateWeek(weekId) { note ->
            val current = note.instances.find { it.taskId == taskId }
            val updated = (current ?: TaskInstance(taskId, weekId)).completing(completed, on).withTitle(task?.title)
            note.copy(instances = note.instances.filterNot { it.taskId == taskId } + updated)
        }
        if (task?.recurrence == Recurrence.ADHOC) {
            val doneOn = if (completed) on else null
            if (task.doneOn != doneOn) save(task.copy(doneOn = doneOn))
        }
    }

    private suspend fun titlesOf(ids: List<String>): Map<String, String> {
        ensureLoaded()
        return items.value.filter { it.id in ids }.associate { it.id to it.title }
    }

    private fun TaskInstance.withTitle(title: String?) = if (title == null) this else copy(taskTitle = title)

    private companion object { const val DIR = "tasks" }
}

class MarkdownThemeRepository(private val vault: VaultFileSystem) : ThemeRepository, VaultRefreshable {
    private val themes = MutableStateFlow<Map<Int, AnnualTheme>>(emptyMap())
    private val loadGuard = Mutex()
    private var loaded = false
    private val stamps = FolderStamps(vault, DIR)
    private val ioGuard = Mutex()

    private suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        themes.value = stamps.changes()?.changed.orEmpty().values
            .mapNotNull(MarkdownCodecs::decodeTheme)
            .associateBy { it.year }
        loaded = true
    }

    override suspend fun refreshFromVault() = ioGuard.withLock {
        if (!loaded) return@withLock
        val changes = stamps.changes()?.takeUnless { it.isEmpty } ?: return@withLock
        val removedYears = changes.removed.mapNotNullTo(mutableSetOf()) { it.noteKey().toIntOrNull() }
        val decoded = changes.changed.values.mapNotNull(MarkdownCodecs::decodeTheme)
        themes.update { current -> current - removedYears + decoded.associateBy { it.year } }
    }

    private suspend fun persist(theme: AnnualTheme) = ioGuard.withLock {
        themes.update { it + (theme.year to theme) }
        vault.write(DIR, "${theme.year}.md", MarkdownCodecs.encodeTheme(theme))
        stamps.written("${theme.year}.md")
    }

    override fun observeTheme(year: Int): Flow<AnnualTheme?> = flow {
        ensureLoaded()
        emitAll(themes.map { it[year] })
    }

    override suspend fun getTheme(year: Int): AnnualTheme? {
        ensureLoaded()
        return themes.value[year]
    }

    override suspend fun upsertTheme(theme: AnnualTheme) {
        ensureLoaded()
        persist(theme)
    }

    override suspend fun setObjectiveAchieved(objectiveId: String, achieved: Boolean, on: LocalDate) {
        ensureLoaded()
        themes.value.values
            .filter { theme -> theme.objectives.any { it.id == objectiveId } }
            .forEach { theme ->
                persist(
                    theme.copy(
                        objectives = theme.objectives.map { obj ->
                            if (obj.id == objectiveId) {
                                obj.copy(achieved = achieved, achievedOn = if (achieved) on else null)
                            } else {
                                obj
                            }
                        },
                    ),
                )
            }
    }

    private companion object { const val DIR = "theme" }
}

class MarkdownPointsLedgerRepository(private val weeks: MarkdownWeekStore) : PointsLedgerRepository {

    // Closed weeks count through their pointsAtWeekEnd checkpoint; only open weeks' events are summed.
    override fun observeBalance(): Flow<Int> = weeks.observeNotes().map(::balanceOf)

    override suspend fun currentBalance(): Int = balanceOf(weeks.snapshot())

    override suspend fun append(event: PointsEvent) =
        weeks.updateWeek(event.weekId) { it.copy(events = it.events + event) }

    override suspend fun eventsForWeek(weekId: String): List<PointsEvent> =
        weeks.snapshot()[weekId]?.events.orEmpty()

    override suspend fun isWeekClosed(weekId: String): Boolean = weeks.isClosed(weekId)

    override fun observeRecordedWeekIds(): Flow<List<String>> =
        weeks.observeNotes().map { notes -> notes.keys.sorted() }

    override suspend fun weeklyReport(weekId: String): WeeklyReport {
        // A closed week's ledger isn't kept loaded: the report reads the whole note on demand.
        val week = weeks.fullNote(weekId)?.events.orEmpty()
        val earned = week.filter { it.delta > 0 }.sumOf { it.delta }
        val spent = week.filter { it.delta < 0 }.sumOf { it.delta }
        return WeeklyReport(
            weekId = weekId,
            completedTasks = week.filter { it.source == PointsSource.TASK && it.delta > 0 },
            achievedObjectives = week.filter { it.source == PointsSource.OBJECTIVE && it.delta > 0 },
            penalties = week.filter { it.source == PointsSource.PENALTY || it.source == PointsSource.HABIT_MISS },
            redemptions = week.filter { it.source == PointsSource.REWARD },
            earned = earned,
            spent = spent,
            net = earned + spent,
            // The balance as the week ended, not today's.
            endingBalance = weekPoints(weeks.snapshot())[weekId]?.end ?: 0,
        )
    }
}

class MarkdownWeeklyReviewRepository(private val weeks: MarkdownWeekStore) : WeeklyReviewRepository {

    override fun observeReview(weekId: String): Flow<WeeklyReview?> =
        weeks.observeNotes().map { it[weekId]?.review }

    // The full note: a closed week keeps its journal in the body, which is only read on demand.
    override suspend fun getReview(weekId: String): WeeklyReview? = weeks.fullNote(weekId)?.review

    override suspend fun upsert(review: WeeklyReview) =
        weeks.updateWeek(review.weekId) { it.copy(review = review) }

    override suspend fun lastSubmittedWeekId(): String? = weeks.snapshot().values
        .mapNotNull { it.review }
        .filter { it.submittedAt != null }
        .maxByOrNull { it.weekId }
        ?.weekId
}

/** Keeps the health sync's daily values in the week note's `## Health` section. */
class MarkdownHealthLogRepository(private val weeks: MarkdownWeekStore) : HealthLogRepository {

    override fun observeWeek(weekId: String): Flow<List<DailyHealth>> =
        weeks.observeNotes().map { it[weekId]?.health.orEmpty() }

    override suspend fun record(weekId: String, days: List<DailyHealth>) {
        val current = weeks.snapshot()[weekId]?.health.orEmpty()
        // Home syncs on every open: rewriting an unchanged note would only churn the vault.
        if (current.mergedWith(days) == current) return
        weeks.updateWeek(weekId) { it.copy(health = it.health.mergedWith(days)) }
    }
}
