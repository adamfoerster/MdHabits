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
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.ui.theme.Paper
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

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
)

/**
 * The design's "form mode" bottom sheet — name, optional points, optional frequency and
 * optional value/objective links, ending in a dark save button. With [initial] the sheet
 * edits an existing entity instead of creating one.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EntityFormSheet(
    kind: EntityKind,
    initial: EntityFormInitial? = null,
    linkOptions: List<LinkOption> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (name: String, points: Int, recurrence: Recurrence, daysOfWeek: List<DayOfWeek>, linkIds: List<String>) -> Unit,
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
                            strings.recurWeekly to Recurrence.WEEKLY,
                            strings.recurDaysOfWeek to Recurrence.DAYS_OF_WEEK,
                            strings.recurAdhoc to Recurrence.ADHOC,
                        )
                        options.forEach { (label, option) ->
                            SelectChip(label = label, selected = recurrence == option) { recurrence = option }
                        }
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
                    (!isTask || recurrence != Recurrence.DAYS_OF_WEEK || daysOfWeek.isNotEmpty()),
            ) {
                val days = if (recurrence == Recurrence.DAYS_OF_WEEK) {
                    daysOfWeek.sortedBy { it.isoDayNumber }
                } else {
                    emptyList()
                }
                onSave(name.trim(), points.toIntOrNull() ?: 0, recurrence, days, linkIds)
            }
        }
    }
}

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
