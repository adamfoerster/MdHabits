package com.adamfoerster.mdhabits.domain.usecase

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.domain.model.planReminders
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Keeps the platform's scheduled reminders in step with the tasks: whenever a task, its completion
 * today, the language or the day changes, the whole set is re-planned and handed to the
 * [ReminderScheduler]. A task already done today doesn't notify again until its next due day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSyncer(
    private val tasks: TaskRepository,
    private val scheduler: ReminderScheduler,
    private val weekCalculator: WeekCalculator,
    private val locale: LocaleController,
    private val dayTickMillis: Long = 60_000,
) {
    /** Suspends forever, rescheduling on every change; a no-op where reminders aren't supported. */
    suspend fun run() {
        if (!scheduler.isSupported) return
        combine(
            tasks.observeTasks(activeOnly = true),
            completedToday(),
            locale.lang,
        ) { all, completedIds, lang ->
            planReminders(all, completedIds, stringsFor(lang).reminderBody)
        }.distinctUntilChanged().collect { scheduler.schedule(it) }
    }

    private fun today(): Flow<LocalDate> = flow {
        while (true) {
            emit(weekCalculator.today())
            delay(dayTickMillis)
        }
    }.distinctUntilChanged()

    private fun completedToday(): Flow<Set<String>> = today().flatMapLatest { day ->
        tasks.observeInstances(weekCalculator.weekId(day)).map { instances ->
            instances.filter { day in it.completedDates }.mapTo(mutableSetOf()) { it.taskId }
        }
    }
}
