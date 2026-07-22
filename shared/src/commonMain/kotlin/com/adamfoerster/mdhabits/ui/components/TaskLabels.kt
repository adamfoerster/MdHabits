package com.adamfoerster.mdhabits.ui.components

import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import kotlinx.datetime.isoDayNumber

/**
 * The lowercase frequency label shown in task subtitles: "weekly", "daily", "ad-hoc", or the
 * chosen weekdays ("mon, wed, fri") for a days-of-week task.
 */
fun Strings.frequencyLabel(task: Task): String = when (task.recurrence) {
    Recurrence.ADHOC -> recurAdhoc.lowercase()
    Recurrence.DAILY -> recurDaily.lowercase()
    Recurrence.WEEKLY -> recurWeekly.lowercase()
    Recurrence.DAYS_OF_WEEK -> task.daysOfWeek
        .sortedBy { it.isoDayNumber }
        .joinToString(", ") { daysShort[it.isoDayNumber - 1].lowercase() }
        .ifEmpty { recurDaysOfWeek.lowercase() }
}
