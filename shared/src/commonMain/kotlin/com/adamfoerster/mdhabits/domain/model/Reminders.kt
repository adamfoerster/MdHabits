package com.adamfoerster.mdhabits.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/** A time of day a task reminder fires at, e.g. 08:30. Stored in the task note as `"08:30"`. */
data class ReminderTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "Invalid reminder time $hour:$minute" }
    }

    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        val DEFAULT = ReminderTime(8, 0)

        /** Parses `"H:mm"` / `"HH:mm"`; null for anything else, so a hand-edited note never fails to load. */
        fun parseOrNull(text: String): ReminderTime? {
            val parts = text.trim().split(':')
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return ReminderTime(hour, minute)
        }
    }
}

/**
 * The weekdays this task reminds on: every day for a daily task or habit, the chosen weekdays for a
 * days-of-week task. Empty when no reminder is set, or for a weekly or ad-hoc task (no particular
 * day to remind about).
 */
val Task.reminderDays: Set<DayOfWeek>
    get() {
        if (reminder == null) return emptySet()
        return when (recurrence) {
            Recurrence.DAILY, Recurrence.HABIT -> DayOfWeek.entries.toSet()
            Recurrence.DAYS_OF_WEEK -> daysOfWeek.toSet()
            Recurrence.WEEKLY, Recurrence.ADHOC -> emptySet()
        }
    }

/**
 * One reminder to hand to the platform: notify with [title]/[body] at [time] on each of [days].
 * [skipToday] is set when the task was already completed today, so today's notification is dropped.
 */
data class ReminderPlan(
    val taskId: String,
    val title: String,
    val body: String,
    val time: ReminderTime,
    val days: Set<DayOfWeek>,
    val skipToday: Boolean = false,
)

/**
 * The reminders to schedule for [tasks]. [completedTodayIds] are the tasks already done today; their
 * notification for today is skipped. Inactive tasks and tasks without a reminder are left out.
 */
fun planReminders(tasks: List<Task>, completedTodayIds: Set<String>, body: String): List<ReminderPlan> =
    tasks.mapNotNull { task ->
        val time = task.reminder ?: return@mapNotNull null
        val days = task.reminderDays
        if (!task.active || days.isEmpty()) return@mapNotNull null
        ReminderPlan(task.id, task.title, body, time, days, skipToday = task.id in completedTodayIds)
    }

/**
 * The next moment [plan] should fire, strictly after [now]: the first allowed day (today included
 * unless [ReminderPlan.skipToday]) whose [ReminderPlan.time] is still ahead. Null when it has no days.
 */
fun nextReminderAt(now: LocalDateTime, plan: ReminderPlan): LocalDateTime? {
    if (plan.days.isEmpty()) return null
    val time = LocalTime(plan.time.hour, plan.time.minute)
    for (offset in 0..7) {
        val date: LocalDate = now.date.plus(offset, DateTimeUnit.DAY)
        if (date.dayOfWeek !in plan.days) continue
        if (offset == 0 && plan.skipToday) continue
        val at = LocalDateTime(date, time)
        if (at > now) return at
    }
    return null
}
