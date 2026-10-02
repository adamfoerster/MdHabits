package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.domain.model.HealthComparison
import com.adamfoerster.mdhabits.domain.model.HealthGoal
import com.adamfoerster.mdhabits.domain.model.HealthMetric
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.ui.theme.Paper
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
)

/**
 * The design's "form mode" bottom sheet — name, optional points, optional frequency and
 * optional value/objective links, ending in a dark save button. With [initial] the sheet
 * edits an existing entity instead of creating one. [healthSupported] adds the task's optional
 * health goal (only where the platform has a health store).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EntityFormSheet(
    kind: EntityKind,
    initial: EntityFormInitial? = null,
    linkOptions: List<LinkOption> = emptyList(),
    healthSupported: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        points: Int,
        recurrence: Recurrence,
        daysOfWeek: List<DayOfWeek>,
        linkIds: List<String>,
        healthGoal: HealthGoal?,
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
                Column {
                    SectionLabel(strings.frequency, Modifier.padding(bottom = 7.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        val options = listOf(
                            strings.recurDaily to Recurrence.DAILY,
                            strings.recurHabit to Recurrence.HABIT,
                            strings.recurWeekly to Recurrence.WEEKLY,
                            strings.recurDaysOfWeek to Recurrence.DAYS_OF_WEEK,
                            strings.recurAdhoc to Recurrence.ADHOC,
                        )
                        options.forEach { (label, option) ->
                            SelectChip(label = label, selected = recurrence == option) { recurrence = option }
                        }
                    }
                    if (recurrence == Recurrence.HABIT) {
                        Text(
                            strings.recurHabitHint,
                            Modifier.padding(top = 7.dp),
                            style = sansStyle(11.5.sp, Paper.muted),
                        )
                    }
                    if (recurrence == Recurrence.DAYS_OF_WEEK) {
                        androidx.compose.foundation.layout.FlowRow(
                            Modifier.padding(top = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            (1..7).forEach { iso ->
                                val day = DayOfWeek(iso)
                                SelectChip(
                                    label = strings.daysShort[iso - 1],
                                    selected = day in daysOfWeek,
                                ) {
                                    daysOfWeek =
                                        if (day in daysOfWeek) daysOfWeek - day else daysOfWeek + day
                                }
                            }
                        }
                    }
                }
                if (healthSupported) {
                    Column {
                        SectionLabel(strings.healthGoal, Modifier.padding(bottom = 7.dp))
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            SelectChip(label = strings.healthGoalNone, selected = healthMetric == null) {
                                healthMetric = null
                            }
                            HealthMetric.entries.forEach { metric ->
                                SelectChip(label = strings.healthMetricName(metric), selected = healthMetric == metric) {
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
                                }
                            }
                        }
                        healthMetric?.let { metric ->
                            Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                SelectChip(
                                    label = strings.healthAtLeast,
                                    selected = healthComparison == HealthComparison.AT_LEAST,
                                ) { healthComparison = HealthComparison.AT_LEAST }
                                SelectChip(
                                    label = strings.healthAtMost,
                                    selected = healthComparison == HealthComparison.AT_MOST,
                                ) { healthComparison = HealthComparison.AT_MOST }
                            }
                            SectionLabel(
                                when (metric) {
                                    HealthMetric.STEPS -> strings.healthTargetSteps
                                    HealthMetric.SLEEP -> strings.healthTargetSleep
                                    HealthMetric.WEIGHT -> strings.healthTargetWeight
                                },
                                Modifier.padding(top = 10.dp, bottom = 5.dp),
                            )
                            PaperTextField(
                                value = healthTarget,
                                onValueChange = { new ->
                                    healthTarget = if (metric == HealthMetric.STEPS) {
                                        new.filter { it.isDigit() }
                                    } else {
                                        new.filter { it.isDigit() || it == '.' || it == ',' }
                                    }
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
                            Text(
                                strings.healthGoalHint,
                                Modifier.padding(top = 7.dp),
                                style = sansStyle(11.5.sp, Paper.muted),
                            )
                        }
                    }
                }
                if (linkOptions.isNotEmpty()) {
                    Column {
                        SectionLabel(strings.linkLabel, Modifier.padding(bottom = 7.dp))
                        FlowChips(
                            options = linkOptions,
                            selectedIds = linkIds,
                            onToggle = { id ->
                                linkIds = if (id in linkIds) linkIds - id else linkIds + id
                            },
                        )
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
                onSave(name.trim(), points.toIntOrNull() ?: 0, recurrence, days, linkIds, healthGoal.takeIf { isTask })
            }
        }
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

/** Wrapping row of selectable pills for value/objective links. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(options: List<LinkOption>, selectedIds: List<String>, onToggle: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        options.forEach { option ->
            SelectChip(
                label = option.label,
                selected = option.id in selectedIds,
            ) { onToggle(option.id) }
        }
    }
}

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
