package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.ReminderTime
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.ui.theme.Paper
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber
import kotlin.math.roundToLong

/** What kind of entity the sheet creates; drives labels and which fields appear. */
enum class EntityKind { VALUE, OBJECTIVE, TASK, PENALTY, REWARD }

/** A value/objective the task form can link the new task to. */
data class LinkOption(val id: String, val label: String, val isValue: Boolean)

/** Pre-filled field values that switch the sheet into edit mode. */
data class EntityFormInitial(
    val name: String = "",
    val points: Int? = null,
    val recurrence: Recurrence = Recurrence.WEEKLY,
    val daysOfWeek: List<DayOfWeek> = emptyList(),
    val linkIds: List<String> = emptyList(),
    val healthGoal: HealthGoal? = null,
    val reminder: ReminderTime? = null,
)

/** Whether a task with this frequency is due on particular days, and so can have a reminder. */
private val Recurrence.canRemind: Boolean
    get() = this == Recurrence.DAILY || this == Recurrence.HABIT || this == Recurrence.DAYS_OF_WEEK

/**
 * The design's "form mode" bottom sheet — name, optional points, and for a task a "When" card
 * (frequency, weekdays), a reminder card and a collapsed "More options" card (health goal, value and
 * objective links), ending in a dark save button. With [initial] the sheet edits an existing entity
 * instead of creating one. [healthSupported] adds the task's optional health goal and
 * [remindersSupported] its reminder (each only where the platform has the matching service);
 * [onRequestReminderPermission] is awaited when the reminder is switched on and returns whether
 * notifications are allowed.
 */
@Composable
fun EntityFormSheet(
    kind: EntityKind,
    initial: EntityFormInitial? = null,
    linkOptions: List<LinkOption> = emptyList(),
    healthSupported: Boolean = false,
    remindersSupported: Boolean = false,
    onRequestReminderPermission: suspend () -> Boolean = { true },
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (
        name: String,
        points: Int,
        recurrence: Recurrence,
        daysOfWeek: List<DayOfWeek>,
        linkIds: List<String>,
        healthGoal: HealthGoal?,
        reminder: ReminderTime?,
    ) -> Unit,
) {
    val strings = LocalStrings.current
    val texts = when (kind) {
        EntityKind.VALUE -> FormTexts(strings.sheetValueTitle, strings.sheetValueEditTitle, strings.sheetValueSub, strings.sheetValueName, strings.sheetValuePh, null)
        EntityKind.OBJECTIVE -> FormTexts(strings.sheetObjectiveTitle, strings.sheetObjectiveEditTitle, strings.sheetObjectiveSub, strings.sheetObjectiveName, strings.sheetObjectivePh, strings.sheetObjectivePts)
        EntityKind.TASK -> FormTexts(strings.sheetTaskTitle, strings.sheetTaskEditTitle, strings.sheetTaskSub, strings.sheetTaskName, strings.sheetTaskPh, strings.sheetTaskPts)
        EntityKind.PENALTY -> FormTexts(strings.sheetPenaltyTitle, strings.sheetPenaltyEditTitle, strings.sheetPenaltySub, strings.sheetPenaltyName, strings.sheetPenaltyPh, strings.sheetPenaltyPts)
        EntityKind.REWARD -> FormTexts(strings.sheetRewardTitle, strings.sheetRewardEditTitle, strings.sheetRewardSub, strings.sheetRewardName, strings.sheetRewardPh, strings.sheetRewardPts)
    }
    val title = if (initial != null) texts.editTitle else texts.title
    val ptsLabel = texts.pointsLabel
    val isTask = kind == EntityKind.TASK
    val scope = rememberCoroutineScope()

    var confirmingDelete by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var points by remember { mutableStateOf(initial?.points?.toString() ?: "") }
    var recurrence by remember { mutableStateOf(initial?.recurrence ?: Recurrence.WEEKLY) }
    var daysOfWeek by remember { mutableStateOf(initial?.daysOfWeek ?: emptyList()) }
    var linkIds by remember { mutableStateOf(initial?.linkIds ?: emptyList()) }
    var healthMetric by remember { mutableStateOf(initial?.healthGoal?.metric) }
    var healthComparison by remember {
        mutableStateOf(initial?.healthGoal?.comparison ?: HealthComparison.AT_LEAST)
    }
    var healthTarget by remember { mutableStateOf(initial?.healthGoal?.let { formTarget(it) } ?: "") }
    val healthGoal = healthMetric?.let { parseHealthGoal(it, healthComparison, healthTarget) }
    var reminder by remember { mutableStateOf(initial?.reminder) }
    var reminderDenied by remember { mutableStateOf(false) }
    var moreExpanded by remember {
        mutableStateOf(initial != null && (initial.healthGoal != null || initial.linkIds.isNotEmpty()))
    }

    PaperSheet(title = title, subtitle = texts.subtitle, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                SectionLabel(texts.nameLabel, Modifier.padding(bottom = 5.dp))
                PaperTextField(value = name, onValueChange = { name = it }, placeholder = texts.namePh)
            }
            if (ptsLabel != null) {
                Column {
                    SectionLabel(ptsLabel, Modifier.padding(bottom = 5.dp))
                    PaperTextField(
                        value = points,
                        onValueChange = { new -> points = new.filter { it.isDigit() } },
                        placeholder = "10",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            if (isTask) {
                FormCard(title = strings.formSectionWhen) {
                    val options = listOf(
                        Triple(Recurrence.DAILY, strings.recurDaily, strings.recurDailyHint),
                        Triple(Recurrence.HABIT, strings.recurHabit, strings.recurHabitHint),
                        Triple(Recurrence.DAYS_OF_WEEK, strings.recurDaysOfWeek, strings.recurDaysOfWeekHint),
                        Triple(Recurrence.WEEKLY, strings.recurWeekly, strings.recurWeeklyHint),
                        Triple(Recurrence.ADHOC, strings.recurAdhoc, strings.recurAdhocHint),
                    )
                    options.forEach { (option, label, hint) ->
                        RadioRow(label = label, selected = recurrence == option, hint = hint) {
                            recurrence = option
                        }
                    }
                    if (recurrence == Recurrence.DAYS_OF_WEEK) {
                        DayCircles(
                            labels = strings.daysShort,
                            selected = daysOfWeek.mapTo(mutableSetOf()) { it.isoDayNumber - 1 },
                        ) { index ->
                            val day = DayOfWeek(index + 1)
                            daysOfWeek = if (day in daysOfWeek) daysOfWeek - day else daysOfWeek + day
                        }
                    }
                }
                if (remindersSupported) {
                    FormCard(title = strings.reminderLabel) {
                        if (!recurrence.canRemind) {
                            Text(strings.reminderUnavailable, style = sansStyle(13.sp, Paper.muted))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    reminder?.let { strings.reminderTime(it.toString()) } ?: strings.reminderOff,
                                    Modifier.weight(1f),
                                    style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold),
                                )
                                PaperSwitch(checked = reminder != null) { on ->
                                    if (!on) {
                                        reminder = null
                                        reminderDenied = false
                                    } else {
                                        scope.launch {
                                            if (onRequestReminderPermission()) {
                                                reminder = ReminderTime.DEFAULT
                                                reminderDenied = false
                                            } else {
                                                reminderDenied = true
                                            }
                                        }
                                    }
                                }
                            }
                            reminder?.let { time ->
                                TimeStepper(time, onChange = { reminder = it })
                                Text(strings.reminderHint, style = sansStyle(11.5.sp, Paper.muted))
                            }
                            if (reminderDenied) {
                                Text(strings.reminderDenied, style = sansStyle(12.sp, Paper.danger))
                            }
                        }
                    }
                }
                if (healthSupported || linkOptions.isNotEmpty()) {
                    val summary = buildList {
                        healthMetric?.let { add(strings.healthMetricName(it)) }
                        addAll(linkOptions.filter { it.id in linkIds }.map { it.label })
                    }.joinToString(" · ").ifEmpty { strings.linksNone }
                    ExpandableSection(
                        title = strings.formSectionMore,
                        summary = summary,
                        expanded = moreExpanded,
                        onToggle = { moreExpanded = !moreExpanded },
                    ) {
                        if (healthSupported) {
                            HealthGoalFields(
                                metric = healthMetric,
                                comparison = healthComparison,
                                target = healthTarget,
                                onMetric = { metric ->
                                    if (healthMetric != metric) {
                                        healthMetric = metric
                                        // Weight goals are usually a ceiling; steps and sleep, a floor.
                                        healthComparison = if (metric == HealthMetric.WEIGHT) {
                                            HealthComparison.AT_MOST
                                        } else {
                                            HealthComparison.AT_LEAST
                                        }
                                        healthTarget = ""
                                    }
                                },
                                onComparison = { healthComparison = it },
                                onTarget = { healthTarget = it },
                            )
                        }
                        if (linkOptions.isNotEmpty()) {
                            SectionLabel(strings.linkLabel)
                            linkOptions.forEach { option ->
                                CheckRow(option.label, option.id in linkIds) {
                                    linkIds = if (option.id in linkIds) linkIds - option.id else linkIds + option.id
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = strings.save,
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() &&
                    (!isTask || recurrence != Recurrence.DAYS_OF_WEEK || daysOfWeek.isNotEmpty()) &&
                    // A chosen health metric needs a usable target before the task can be saved.
                    (!isTask || healthMetric == null || healthGoal != null),
            ) {
                val days = if (recurrence == Recurrence.DAYS_OF_WEEK) {
                    daysOfWeek.sortedBy { it.isoDayNumber }
                } else {
                    emptyList()
                }
                val savedReminder = when {
                    !isTask -> null
                    // Without reminder support the form can't show it; keep what the note already says.
                    !remindersSupported -> initial?.reminder
                    recurrence.canRemind -> reminder
                    else -> null
                }
                onSave(
                    name.trim(), points.toIntOrNull() ?: 0, recurrence, days, linkIds,
                    healthGoal.takeIf { isTask }, savedReminder,
                )
            }
            if (onDelete != null) {
                if (confirmingDelete) {
                    Text(strings.deleteTaskConfirm, style = sansStyle(13.sp, Paper.muted))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SelectChip(label = strings.cancel, selected = false) { confirmingDelete = false }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(Paper.danger)
                                .paperClick(onClick = onDelete)
                                .padding(horizontal = 18.dp, vertical = 9.dp),
                        ) {
                            Text(strings.deleteTask, style = sansStyle(13.sp, Paper.onDark, FontWeight.Bold))
                        }
                    }
                } else {
                    Text(
                        strings.deleteTask,
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .paperClick { confirmingDelete = true }
                            .padding(8.dp),
                        style = sansStyle(14.sp, Paper.danger, FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** The optional health goal: a metric (or none), whether the target is a floor or a ceiling, and the target. */
@Composable
private fun HealthGoalFields(
    metric: HealthMetric?,
    comparison: HealthComparison,
    target: String,
    onMetric: (HealthMetric?) -> Unit,
    onComparison: (HealthComparison) -> Unit,
    onTarget: (String) -> Unit,
) {
    val strings = LocalStrings.current
    SectionLabel(strings.healthGoal)
    val metrics = HealthMetric.entries
    SegmentedControl(
        options = listOf(strings.healthGoalNone) + metrics.map { strings.healthMetricName(it) },
        selectedIndex = metric?.let { metrics.indexOf(it) + 1 } ?: 0,
    ) { index -> onMetric(metrics.getOrNull(index - 1)) }
    if (metric != null) {
        SegmentedControl(
            options = listOf(strings.healthAtLeast, strings.healthAtMost),
            selectedIndex = if (comparison == HealthComparison.AT_LEAST) 0 else 1,
        ) { onComparison(if (it == 0) HealthComparison.AT_LEAST else HealthComparison.AT_MOST) }
        SectionLabel(
            when (metric) {
                HealthMetric.STEPS -> strings.healthTargetSteps
                HealthMetric.SLEEP -> strings.healthTargetSleep
                HealthMetric.WEIGHT -> strings.healthTargetWeight
            },
        )
        PaperTextField(
            value = target,
            onValueChange = { new ->
                onTarget(
                    if (metric == HealthMetric.STEPS) {
                        new.filter { it.isDigit() }
                    } else {
                        new.filter { it.isDigit() || it == '.' || it == ',' }
                    },
                )
            },
            placeholder = when (metric) {
                HealthMetric.STEPS -> "8000"
                HealthMetric.SLEEP -> "7.5"
                HealthMetric.WEIGHT -> "80"
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (metric == HealthMetric.STEPS) KeyboardType.Number else KeyboardType.Decimal,
            ),
        )
        Text(strings.healthGoalHint, style = sansStyle(11.5.sp, Paper.muted))
    }
}

/**
 * The goal typed in the form, or null while the target isn't a positive number. Sleep is typed in
 * hours (people think "7.5 hours", not "450 minutes") and stored in minutes.
 */
internal fun parseHealthGoal(metric: HealthMetric, comparison: HealthComparison, target: String): HealthGoal? {
    val value = target.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    val stored = if (metric == HealthMetric.SLEEP) (value * 60).roundToLong().toDouble() else value
    return HealthGoal(metric, comparison, stored)
}

/** The goal's target as the form shows it: sleep back in hours, no trailing `.0`. */
internal fun formTarget(goal: HealthGoal): String =
    formatDecimal(if (goal.metric == HealthMetric.SLEEP) goal.target / 60 else goal.target)

private data class FormTexts(
    val title: String,
    val editTitle: String,
    val subtitle: String,
    val nameLabel: String,
    val namePh: String,
    val pointsLabel: String?,
)

/** Single-select sheet: tap a task to link it (e.g. to the mdPrayer integration in Settings). */
@Composable
fun TaskPickerSheet(
    title: String,
    subtitle: String,
    tasks: List<Task>,
    selectedTaskId: String?,
    onDismiss: () -> Unit,
    onSelect: (Task) -> Unit,
) {
    PaperSheet(title = title, subtitle = subtitle, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tasks.forEach { task ->
                val selected = task.id == selectedTaskId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .paperCard(radius = 13, border = if (selected) Paper.accent else Paper.border)
                        .paperClick { onSelect(task) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphPlate(if (selected) "✓" else "○", hand = false)
                    Text(
                        task.title,
                        Modifier.weight(1f),
                        style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

/** The design's "penalty list mode" sheet: tap a penalty to apply it. */
@Composable
fun PenaltyListSheet(
    penalties: List<com.adamfoerster.mdhabits.domain.model.Penalty>,
    onDismiss: () -> Unit,
    onApply: (com.adamfoerster.mdhabits.domain.model.Penalty) -> Unit,
) {
    val strings = LocalStrings.current
    PaperSheet(title = strings.penaltySheetTitle, subtitle = strings.penaltySheetSub, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            penalties.forEach { penalty ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .paperCard(radius = 13)
                        .paperClick { onApply(penalty) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphPlate("−", bg = Paper.dangerSoft, color = Paper.danger, hand = false)
                    Text(
                        penalty.name,
                        Modifier.weight(1f),
                        style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold),
                    )
                    Text(
                        "−${penalty.pointCost}",
                        style = sansStyle(14.sp, Paper.danger, FontWeight.Bold),
                    )
                }
            }
        }
    }
}
