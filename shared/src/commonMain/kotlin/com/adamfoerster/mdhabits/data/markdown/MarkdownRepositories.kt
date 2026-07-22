package com.adamfoerster.mdhabits.data.markdown

import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.PointsSource
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReport
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
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

/** Shared load-once + write-through plumbing for the simple `<id>.md`-per-entity folders. */
abstract class MarkdownCrudRepository<T>(
    private val vault: VaultFileSystem,
    private val dir: String,
) {
    protected val items = MutableStateFlow<List<T>>(emptyList())
    private val loadGuard = Mutex()
    private var loaded = false

    protected abstract fun idOf(item: T): String
    protected abstract fun encode(item: T): String
    protected abstract fun decode(text: String): T?

    protected suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        items.value = vault.list(dir).mapNotNull { name ->
            vault.read(dir, name)?.let(::decode)
        }
        loaded = true
    }

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
        items.update { list ->
            if (list.any { idOf(it) == idOf(item) }) {
                list.map { if (idOf(it) == idOf(item)) item else it }
            } else {
                list + item
            }
        }
        vault.write(dir, "${idOf(item)}.md", encode(item))
    }

    protected suspend fun remove(id: String) {
        ensureLoaded()
        items.update { list -> list.filterNot { idOf(it) == id } }
        vault.delete(dir, "$id.md")
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

    override fun observeWeekStarted(weekId: String): Flow<Boolean> =
        weeks.observeNotes().map { weekId in it }

    override suspend fun setPlanned(weekId: String, taskIds: List<String>) = weeks.updateWeek(weekId) { note ->
        val existing = note.instances.associateBy { it.taskId }
        val updated = taskIds.map { id ->
            existing[id]?.copy(planned = true) ?: TaskInstance(id, weekId, planned = true)
        }
        // Keep completed instances for tasks no longer planned so history is preserved.
        val keptCompleted = existing.values.filter { it.taskId !in taskIds && it.completed }
            .map { it.copy(planned = false) }
        note.copy(instances = updated + keptCompleted)
    }

    override suspend fun setCompleted(taskId: String, weekId: String, completed: Boolean, on: LocalDate) =
        weeks.updateWeek(weekId) { note ->
            val current = note.instances.find { it.taskId == taskId }
            val updated = (current ?: TaskInstance(taskId, weekId)).copy(
                completed = completed,
                completedOn = if (completed) on else null,
            )
            note.copy(instances = note.instances.filterNot { it.taskId == taskId } + updated)
        }

    private companion object { const val DIR = "tasks" }
}

class MarkdownThemeRepository(private val vault: VaultFileSystem) : ThemeRepository {
    private val themes = MutableStateFlow<Map<Int, AnnualTheme>>(emptyMap())
    private val loadGuard = Mutex()
    private var loaded = false

    private suspend fun ensureLoaded() = loadGuard.withLock {
        if (loaded) return@withLock
        themes.value = vault.list(DIR)
            .mapNotNull { name -> vault.read(DIR, name)?.let(MarkdownCodecs::decodeTheme) }
            .associateBy { it.year }
        loaded = true
    }

    private suspend fun persist(theme: AnnualTheme) {
        themes.update { it + (theme.year to theme) }
        vault.write(DIR, "${theme.year}.md", MarkdownCodecs.encodeTheme(theme))
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

    // weekIds sort chronologically, keeping the ledger in order across weeks.
    private fun allEvents(notes: Map<String, WeekNote>): List<PointsEvent> =
        notes.keys.sorted().flatMap { notes.getValue(it).events }

    override fun observeBalance(): Flow<Int> =
        weeks.observeNotes().map { notes -> allEvents(notes).sumOf { it.delta } }

    override suspend fun currentBalance(): Int = allEvents(weeks.snapshot()).sumOf { it.delta }

    override suspend fun append(event: PointsEvent) =
        weeks.updateWeek(event.weekId) { it.copy(events = it.events + event) }

    override suspend fun eventsForWeek(weekId: String): List<PointsEvent> =
        weeks.snapshot()[weekId]?.events.orEmpty()

    override suspend fun weeklyReport(weekId: String): WeeklyReport {
        val notes = weeks.snapshot()
        val week = notes[weekId]?.events.orEmpty()
        val earned = week.filter { it.delta > 0 }.sumOf { it.delta }
        val spent = week.filter { it.delta < 0 }.sumOf { it.delta }
        return WeeklyReport(
            weekId = weekId,
            completedTasks = week.filter { it.source == PointsSource.TASK && it.delta > 0 },
            achievedObjectives = week.filter { it.source == PointsSource.OBJECTIVE && it.delta > 0 },
            penalties = week.filter { it.source == PointsSource.PENALTY },
            redemptions = week.filter { it.source == PointsSource.REWARD },
            earned = earned,
            spent = spent,
            net = earned + spent,
            endingBalance = allEvents(notes).sumOf { it.delta },
        )
    }
}

class MarkdownWeeklyReviewRepository(private val weeks: MarkdownWeekStore) : WeeklyReviewRepository {

    override fun observeReview(weekId: String): Flow<WeeklyReview?> =
        weeks.observeNotes().map { it[weekId]?.review }

    override suspend fun getReview(weekId: String): WeeklyReview? = weeks.snapshot()[weekId]?.review

    override suspend fun upsert(review: WeeklyReview) =
        weeks.updateWeek(review.weekId) { it.copy(review = review) }

    override suspend fun lastSubmittedWeekId(): String? = weeks.snapshot().values
        .mapNotNull { it.review }
        .filter { it.submittedAt != null }
        .maxByOrNull { it.weekId }
        ?.weekId
}
