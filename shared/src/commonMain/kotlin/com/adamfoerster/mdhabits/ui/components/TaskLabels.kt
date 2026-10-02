package com.adamfoerster.mdhabits.ui.components

import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import kotlinx.datetime.isoDayNumber
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The lowercase frequency label shown in task subtitles: "weekly", "daily", "habit", "ad-hoc", or
 * the chosen weekdays ("mon, wed, fri") for a days-of-week task.
 */
fun Strings.frequencyLabel(task: Task): String = when (task.recurrence) {
    Recurrence.ADHOC -> recurAdhoc.lowercase()
    Recurrence.DAILY -> recurDaily.lowercase()
    Recurrence.HABIT -> recurHabit.lowercase()
    Recurrence.WEEKLY -> recurWeekly.lowercase()
    Recurrence.DAYS_OF_WEEK -> task.daysOfWeek
        .sortedBy { it.isoDayNumber }
        .joinToString(", ") { daysShort[it.isoDayNumber - 1].lowercase() }
        .ifEmpty { recurDaysOfWeek.lowercase() }
}

/** The metric's display name: "Steps", "Sleep", "Weight". */
fun Strings.healthMetricName(metric: HealthMetric): String = when (metric) {
    HealthMetric.STEPS -> healthSteps
    HealthMetric.SLEEP -> healthSleep
    HealthMetric.WEIGHT -> healthWeight
}

/** A value in the metric's unit, as people read it: `8000`, `7h12` (sleep minutes), `79.4 kg`. */
fun formatHealthValue(metric: HealthMetric, value: Double): String = when (metric) {
    HealthMetric.STEPS -> value.roundToLong().toString()
    HealthMetric.SLEEP -> {
        val minutes = value.roundToLong()
        val rest = minutes % 60
        "${minutes / 60}h" + if (rest == 0L) "" else rest.toString().padStart(2, '0')
    }
    HealthMetric.WEIGHT -> "${formatDecimal(value)} kg"
}

/**
 * A health-goal task's line under its title on Home: today's value against the goal, e.g.
 * "Steps: 6240 · ≥ 8000" or "Sleep: — · ≥ 7h" when nothing was recorded yet.
 */
fun Strings.healthProgressLabel(goal: HealthGoal, today: Double?): String {
    val symbol = if (goal.comparison == HealthComparison.AT_LEAST) "≥" else "≤"
    val current = today?.let { formatHealthValue(goal.metric, it) } ?: "—"
    return "${healthMetricName(goal.metric)}: $current · $symbol ${formatHealthValue(goal.metric, goal.target)}"
}

/** One decimal at most, without a trailing `.0`: `80`, `79.4`. */
internal fun formatDecimal(value: Double): String {
    val tenths = (value * 10).roundToLong()
    return if (tenths % 10 == 0L) (tenths / 10).toString() else "${tenths / 10}.${abs(tenths % 10)}"
}
