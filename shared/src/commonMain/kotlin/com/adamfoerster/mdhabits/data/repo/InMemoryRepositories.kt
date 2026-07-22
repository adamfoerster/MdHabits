package com.adamfoerster.mdhabits.data.repo

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * In-memory implementations used until the Markdown-backed storage arrives (Phase 6). They live
 * as Koin singletons so all screens share the same state within a run.
 */

class InMemoryValueRepository : ValueRepository {
    private val values = MutableStateFlow<List<PersonalValue>>(emptyList())
    override fun observeValues(): Flow<List<PersonalValue>> = values
    override suspend fun getValue(id: String): PersonalValue? = values.value.find { it.id == id }
    override suspend fun upsert(value: PersonalValue) = values.update { it.upsertBy(value) { v -> v.id == value.id } }
    override suspend fun delete(id: String) = values.update { list -> list.filterNot { it.id == id } }
}

class InMemoryPenaltyRepository : PenaltyRepository {
    private val penalties = MutableStateFlow<List<Penalty>>(emptyList())
    override fun observePenalties(): Flow<List<Penalty>> = penalties
    override suspend fun getPenalty(id: String): Penalty? = penalties.value.find { it.id == id }
    override suspend fun upsert(penalty: Penalty) = penalties.update { it.upsertBy(penalty) { p -> p.id == penalty.id } }
    override suspend fun delete(id: String) = penalties.update { list -> list.filterNot { it.id == id } }
}

class InMemoryRewardRepository : RewardRepository {
    private val rewards = MutableStateFlow<List<Reward>>(emptyList())
    override fun observeRewards(): Flow<List<Reward>> = rewards
    override suspend fun getReward(id: String): Reward? = rewards.value.find { it.id == id }
    override suspend fun upsert(reward: Reward) = rewards.update { it.upsertBy(reward) { r -> r.id == reward.id } }
    override suspend fun delete(id: String) = rewards.update { list -> list.filterNot { it.id == id } }
}

class InMemoryTaskRepository : TaskRepository {
    private val tasks = MutableStateFlow<List<Task>>(emptyList())
    private val instances = MutableStateFlow<List<TaskInstance>>(emptyList())
    private val startedWeeks = MutableStateFlow<Set<String>>(emptySet())

    override fun observeTasks(activeOnly: Boolean): Flow<List<Task>> =
        tasks.map { list -> if (activeOnly) list.filter { it.active } else list }

    override suspend fun getTask(id: String): Task? = tasks.value.find { it.id == id }
    override suspend fun upsert(task: Task) = tasks.update { it.upsertBy(task) { t -> t.id == task.id } }
    override suspend fun delete(id: String) = tasks.update { list -> list.filterNot { it.id == id } }

    override fun observeInstances(weekId: String): Flow<List<TaskInstance>> =
        instances.map { list -> list.filter { it.weekId == weekId } }

    override fun observeWeekStarted(weekId: String): Flow<Boolean> =
        startedWeeks.map { weekId in it }

    override suspend fun setPlanned(weekId: String, taskIds: List<String>) {
        startedWeeks.update { it + weekId }
        instances.update { list ->
            val others = list.filterNot { it.weekId == weekId }
            val existing = list.filter { it.weekId == weekId }.associateBy { it.taskId }
            val updated = taskIds.map { id ->
                existing[id]?.copy(planned = true) ?: TaskInstance(id, weekId, planned = true)
            }
            // Keep completed instances for tasks no longer planned so history is preserved.
            val keptCompleted = existing.values.filter { it.taskId !in taskIds && it.completed }
                .map { it.copy(planned = false) }
            others + updated + keptCompleted
        }
    }

    override suspend fun setCompleted(taskId: String, weekId: String, completed: Boolean, on: LocalDate) {
        startedWeeks.update { it + weekId }
        instances.update { list ->
            val current = list.find { it.taskId == taskId && it.weekId == weekId }
            val updated = (current ?: TaskInstance(taskId, weekId)).copy(
                completed = completed,
                completedOn = if (completed) on else null,
            )
            list.filterNot { it.taskId == taskId && it.weekId == weekId } + updated
        }
    }
}

class InMemoryThemeRepository : ThemeRepository {
    private val themes = MutableStateFlow<Map<Int, AnnualTheme>>(emptyMap())

    override fun observeTheme(year: Int): Flow<AnnualTheme?> = themes.map { it[year] }
    override suspend fun getTheme(year: Int): AnnualTheme? = themes.value[year]
    override suspend fun upsertTheme(theme: AnnualTheme) = themes.update { it + (theme.year to theme) }

    override suspend fun setObjectiveAchieved(objectiveId: String, achieved: Boolean, on: LocalDate) =
        themes.update { map ->
            map.mapValues { (_, theme) ->
                theme.copy(
                    objectives = theme.objectives.map { obj ->
                        if (obj.id == objectiveId) {
                            obj.copy(achieved = achieved, achievedOn = if (achieved) on else null)
                        } else {
                            obj
                        }
                    },
                )
            }
        }
}

class InMemoryPointsLedgerRepository : PointsLedgerRepository {
    private val events = MutableStateFlow<List<PointsEvent>>(emptyList())

    override fun observeBalance(): Flow<Int> = events.map { list -> list.sumOf { it.delta } }
    override suspend fun currentBalance(): Int = events.value.sumOf { it.delta }
    override suspend fun append(event: PointsEvent) = events.update { it + event }
    override suspend fun eventsForWeek(weekId: String): List<PointsEvent> =
        events.value.filter { it.weekId == weekId }

    override suspend fun weeklyReport(weekId: String): WeeklyReport {
        val all = events.value
        val week = all.filter { it.weekId == weekId }
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
            endingBalance = all.sumOf { it.delta },
        )
    }
}

class InMemoryWeeklyReviewRepository : WeeklyReviewRepository {
    private val reviews = MutableStateFlow<Map<String, WeeklyReview>>(emptyMap())

    override fun observeReview(weekId: String): Flow<WeeklyReview?> = reviews.map { it[weekId] }
    override suspend fun getReview(weekId: String): WeeklyReview? = reviews.value[weekId]
    override suspend fun upsert(review: WeeklyReview) = reviews.update { it + (review.weekId to review) }
    override suspend fun lastSubmittedWeekId(): String? =
        reviews.value.values.filter { it.submittedAt != null }.maxByOrNull { it.weekId }?.weekId
}

private inline fun <T> List<T>.upsertBy(item: T, predicate: (T) -> Boolean): List<T> =
    if (any(predicate)) map { if (predicate(it)) item else it } else this + item
