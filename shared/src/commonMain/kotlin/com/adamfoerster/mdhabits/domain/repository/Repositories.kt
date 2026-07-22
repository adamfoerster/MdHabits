package com.adamfoerster.mdhabits.domain.repository

import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.model.WeeklyReport
import com.adamfoerster.mdhabits.domain.model.WeeklyReview
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Repository interfaces. The storage layer (in-memory fakes for the MVP, Markdown-backed later)
 * implements these; nothing above this line knows about files or Markdown.
 */

interface ThemeRepository {
    fun observeTheme(year: Int): Flow<AnnualTheme?>
    suspend fun getTheme(year: Int): AnnualTheme?
    suspend fun upsertTheme(theme: AnnualTheme)
    suspend fun setObjectiveAchieved(objectiveId: String, achieved: Boolean, on: LocalDate)
}

interface ValueRepository {
    fun observeValues(): Flow<List<PersonalValue>>
    suspend fun getValue(id: String): PersonalValue?
    suspend fun upsert(value: PersonalValue)
    suspend fun delete(id: String)
}

interface PenaltyRepository {
    fun observePenalties(): Flow<List<Penalty>>
    suspend fun getPenalty(id: String): Penalty?
    suspend fun upsert(penalty: Penalty)
    suspend fun delete(id: String)
}

interface TaskRepository {
    fun observeTasks(activeOnly: Boolean = true): Flow<List<Task>>
    suspend fun getTask(id: String): Task?
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)

    // Per-week state.
    fun observeInstances(weekId: String): Flow<List<TaskInstance>>

    /** Whether the week's note exists yet — created by planning it (weekly review, onboarding)
     *  or by completing a task in it. Drives the Home weekly-review call to action. */
    fun observeWeekStarted(weekId: String): Flow<Boolean>
    suspend fun setPlanned(weekId: String, taskIds: List<String>)
    suspend fun setCompleted(taskId: String, weekId: String, completed: Boolean, on: LocalDate)
}

interface RewardRepository {
    fun observeRewards(): Flow<List<Reward>>
    suspend fun getReward(id: String): Reward?
    suspend fun upsert(reward: Reward)
    suspend fun delete(id: String)
}

interface PointsLedgerRepository {
    fun observeBalance(): Flow<Int>
    suspend fun currentBalance(): Int
    suspend fun append(event: PointsEvent)
    suspend fun eventsForWeek(weekId: String): List<PointsEvent>
    suspend fun weeklyReport(weekId: String): WeeklyReport
}

interface WeeklyReviewRepository {
    fun observeReview(weekId: String): Flow<WeeklyReview?>
    suspend fun getReview(weekId: String): WeeklyReview?
    suspend fun upsert(review: WeeklyReview)
    /** The most recent week that has a submitted review, or null. Drives the "time for a review" prompt. */
    suspend fun lastSubmittedWeekId(): String?
}
