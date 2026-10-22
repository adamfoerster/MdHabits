package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamfoerster.mdhabits.domain.model.ReminderTime
import com.adamfoerster.mdhabits.ui.theme.Paper

/* Building blocks of the task form: grouped cards, radio rows, a segmented control, day circles,
 * a time stepper and a collapsible section — instead of a wall of pills. */

/** A bordered card that groups related fields of the form under an optional [title]. */
@Composable
fun FormCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().paperCard(radius = 13).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null) SectionLabel(title)
        content()
    }
}

/** One option of a single-choice list: a radio mark, the [label] and, when selected, its [hint]. */
@Composable
fun RadioRow(label: String, selected: Boolean, hint: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).paperClick(onClick = onClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape)
                .border(1.7.dp, if (selected) Paper.accent else Paper.borderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(Paper.accent))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = sansStyle(15.sp, Paper.ink, if (selected) FontWeight.Bold else FontWeight.SemiBold))
            if (selected && hint != null) {
                Text(hint, Modifier.padding(top = 2.dp), style = sansStyle(11.5.sp, Paper.muted))
            }
        }
    }
}

/** A row of mutually exclusive [options] sharing one outline; the selected segment is filled. */
@Composable
fun SegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(11.dp)
    Row(Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Paper.borderStrong, shape)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .background(if (selected) Paper.ink else Paper.card)
                    .paperClick { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = sansStyle(13.sp, if (selected) Paper.onDark else Paper.muted, FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Seven round weekday toggles, Monday first; [selected] holds the ISO indices (0 = Monday) that are on. */
@Composable
fun DayCircles(labels: List<String>, selected: Set<Int>, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val on = index in selected
            Box(
                Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(if (on) Paper.ink else Paper.card)
                    .border(1.5.dp, if (on) Paper.ink else Paper.borderStrong, CircleShape)
                    .paperClick { onToggle(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.take(3),
                    style = sansStyle(11.sp, if (on) Paper.onDark else Paper.muted, FontWeight.SemiBold),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Hour and minute steppers (minutes move in [minuteStep]s) for picking a [ReminderTime] without a dialog. */
@Composable
fun TimeStepper(time: ReminderTime, onChange: (ReminderTime) -> Unit, minuteStep: Int = 5) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperColumn(
            value = time.hour.toString().padStart(2, '0'),
            onUp = { onChange(time.copy(hour = (time.hour + 1) % 24)) },
            onDown = { onChange(time.copy(hour = (time.hour + 23) % 24)) },
        )
        Text(":", Modifier.padding(horizontal = 10.dp), style = handStyle(34.sp, Paper.subtle))
        StepperColumn(
            value = time.minute.toString().padStart(2, '0'),
            onUp = { onChange(time.copy(minute = (time.minute + minuteStep) % 60)) },
            onDown = { onChange(time.copy(minute = (time.minute - minuteStep + 60) % 60)) },
        )
    }
}

@Composable
private fun StepperColumn(value: String, onUp: () -> Unit, onDown: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StepperButton("+", onUp)
        Text(value, Modifier.padding(vertical = 4.dp), style = sansStyle(26.sp, Paper.ink, FontWeight.Bold))
        StepperButton("−", onDown)
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 56.dp, height = 32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Paper.inset)
            .paperClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = sansStyle(18.sp, Paper.subtle, FontWeight.Bold))
    }
}

/** A checkbox row: used to pick the values and objectives a task links to. */
@Composable
fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).paperClick(onClick = onClick).padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CommitCheckbox(checked)
        Text(label, Modifier.weight(1f), style = sansStyle(14.sp, Paper.ink, FontWeight.SemiBold))
    }
}

/** A header that shows a one-line [summary] and reveals [content] when [expanded]. */
@Composable
fun ExpandableSection(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    FormCard {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).paperClick(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                SectionLabel(title)
                if (!expanded) Text(summary, Modifier.padding(top = 2.dp), style = sansStyle(13.sp, Paper.muted))
            }
            Text(if (expanded) "▴" else "▾", style = sansStyle(16.sp, Paper.subtle, FontWeight.Bold))
        }
        if (expanded) content()
    }
}
