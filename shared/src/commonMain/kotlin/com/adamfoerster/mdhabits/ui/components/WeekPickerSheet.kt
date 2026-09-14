package com.adamfoerster.mdhabits.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.adamfoerster.mdhabits.core.datetime.MonthGrid
import com.adamfoerster.mdhabits.core.datetime.WeekRow
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.ui.theme.Paper

/**
 * The calendar that opens from Home's week badge: one month at a time, a row per ISO week. Only
 * weeks the journal has records for are tappable — the rest are there for orientation, greyed out,
 * like the days spilling in from the neighbouring months.
 */
@Composable
fun WeekPickerSheet(
    grid: MonthGrid,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onMonthChange: (monthsBackDelta: Int) -> Unit,
    onSelectWeek: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    PaperSheet(
        title = strings.weekPickerTitle,
        subtitle = strings.weekPickerSub,
        onDismiss = onDismiss,
    ) {
        // Month header: ‹ JUL 2026 ›
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow(left = true, enabled = canGoBack) { onMonthChange(1) }
            Text(
                "${strings.monthsShort[grid.month - 1].uppercase()} ${grid.year}",
                Modifier.weight(1f),
                style = sansStyle(14.sp, Paper.ink, FontWeight.Bold).copy(letterSpacing = 1.2.sp),
                textAlign = TextAlign.Center,
            )
            MonthArrow(left = false, enabled = canGoForward) { onMonthChange(-1) }
        }

        // Weekday initials, aligned with the day cells (the week-number column sits to the left).
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(Modifier.width(WEEK_LABEL_WIDTH))
            strings.daysShort.forEach { day ->
                Text(
                    day.take(1).uppercase(),
                    Modifier.weight(1f),
                    style = sansStyle(10.sp, Paper.faded, FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            grid.weeks.forEach { week ->
                // A compact, localized week tag: "W27" / "S27".
                val tag = strings.weekPrefix.take(1).uppercase() + week.weekNumber
                WeekPickerRow(week, grid.month, tag) { onSelectWeek(week.weekId) }
            }
        }

        if (grid.weeks.none { it.hasRecords }) {
            Text(
                strings.weekPickerEmpty,
                Modifier.padding(top = 12.dp),
                style = sansStyle(12.sp, Paper.muted),
            )
        }
    }
}

private val WEEK_LABEL_WIDTH = 38.dp

@Composable
private fun WeekPickerRow(week: WeekRow, month: Int, weekLabel: String, onSelect: () -> Unit) {
    val open = week.hasRecords
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (open) Modifier.paperCard(radius = 11) else Modifier)
            .paperClick(enabled = open, onClick = onSelect)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            weekLabel,
            Modifier.width(WEEK_LABEL_WIDTH).padding(start = 8.dp),
            style = sansStyle(10.sp, if (open) Paper.accent else Paper.faded, FontWeight.Bold),
        )
        week.days.forEach { day ->
            val inMonth = day.monthNumber == month
            Text(
                day.dayOfMonth.toString(),
                Modifier.weight(1f),
                style = sansStyle(
                    13.sp,
                    when {
                        open && inMonth -> Paper.ink
                        open -> Paper.muted
                        else -> Paper.faded.copy(alpha = 0.55f)
                    },
                    if (open && inMonth) FontWeight.SemiBold else FontWeight.Normal,
                ),
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (open) Chevron(left = false, color = Paper.accent, size = 14.dp)
        }
    }
}

@Composable
private fun MonthArrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) Paper.card else Paper.bg)
            .paperClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Chevron(left = left, color = if (enabled) Paper.ink else Paper.faded.copy(alpha = 0.5f), size = 16.dp)
    }
}
