package com.adamfoerster.mdhabits.domain.usecase

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.TaskInstance
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/** One row of the home-screen widget: a task and whether it is done for today (or this week). */
data class WidgetTask(
    val taskId: String,
    val title: String,
    val points: Int,
    val recurrence: Recurrence,
    val completed: Boolean,
)

/** What the home-screen widget lists: the tasks due today, then the weekly ones. Ad-hoc tasks stay off. */
data class WidgetSnapshot(
    val today: List<WidgetTask>,
    val week: List<WidgetTask>,
) {
    val isEmpty: Boolean get() = today.isEmpty() && week.isEmpty()
}

/**
 * The widget's rows for [today]: the per-day tasks due on it (daily, habits, and days-of-week tasks
 * scheduled for it), each done only when checked off on that day, and the weekly tasks, done once for
 * the week. Each list puts the pending ones first, then sorts by title — like Home.
 */
fun widgetSnapshot(tasks: List<Task>, instances: List<TaskInstance>, today: LocalDate): WidgetSnapshot {
    val byTask = instances.associateBy { it.taskId }
    fun row(task: Task, completed: Boolean) = WidgetTask(task.id, task.title, task.points, task.recurrence, completed)
    val order = compareBy<WidgetTask>({ it.completed }, { it.title })
    val active = tasks.filter { it.active }
    return WidgetSnapshot(
        today = active
            .filter { it.isPerDay && it.isDueOn(today.dayOfWeek) }
            .map { row(it, today in byTask[it.id]?.completedDates.orEmpty()) }
            .sortedWith(order),
        week = active
            .filter { it.recurrence == Recurrence.WEEKLY }
            .map { row(it, byTask[it.id]?.completed == true) }
            .sortedWith(order),
    )
}

/**
 * Backs the Android home-screen widget: reads the current snapshot and checks a task off (or undoes
 * it) for today / the current week, through [CompleteTaskUseCase] so the ledger follows.
 */
class WidgetTasksUseCase(
    private val tasks: TaskRepository,
    private val completeTask: CompleteTaskUseCase,
    private val weekCalculator: WeekCalculator,
) {
    suspend fun snapshot(): WidgetSnapshot {
        val today = weekCalculator.today()
        return widgetSnapshot(
            tasks.observeTasks(activeOnly = true).first(),
            tasks.observeInstances(weekCalculator.weekId(today)).first(),
            today,
        )
    }

    /**
     * Flips [taskId]'s completion for today (a per-day task) or the current week (a weekly task).
     * Does nothing for a task the widget doesn't list — ad-hoc, inactive, deleted, or not due today —
     * so a stale widget can't complete the wrong thing. Returns whether anything changed.
     */
    suspend fun toggle(taskId: String): Boolean {
        val today = weekCalculator.today()
        val snapshot = snapshot()
        val row = (snapshot.today + snapshot.week).find { it.taskId == taskId } ?: return false
        val task = tasks.getTask(taskId) ?: return false
        completeTask(task, weekCalculator.weekId(today), nowCompleted = !row.completed, on = today)
        return true
    }
}
