package com.adamfoerster.mdhabits.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.domain.model.PointsEvent
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.SectionLabel
import com.adamfoerster.mdhabits.ui.components.dashedBorder
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

/**
 * The report of a week the user picked from Home's calendar. Everything here is read-only: a
 * closed week is history, so it shows what the ledger recorded and the journal that was written,
 * with nothing to toggle or type into.
 */
@Composable
fun WeekReportScreen(
    weekId: String,
    onBack: () -> Unit,
    viewModel: WeekReportViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(weekId) { viewModel.show(weekId) }

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier
                    .padding(start = 20.dp, top = 6.dp, bottom = 8.dp)
                    .paperClick(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chevron(left = true, color = Paper.subtle, size = 16.dp)
                Text(strings.back, style = sansStyle(13.sp, Paper.subtle, FontWeight.SemiBold))
            }

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                Kicker(strings.week(state.weekNumber))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.revReport, Modifier.weight(1f), style = handStyle(34.sp))
                    ReadOnlyTag(strings)
                }
                state.range?.let { range ->
                    Text(
                        "${strings.shortDate(range.start.dayOfMonth, range.start.monthNumber)} – " +
                            strings.shortDate(range.endInclusive.dayOfMonth, range.endInclusive.monthNumber),
                        Modifier.padding(top = 2.dp),
                        style = sansStyle(12.5.sp, Paper.muted, FontWeight.Medium),
                    )
                }
            }

            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val report = state.report
                val stats = listOf(
                    Triple("${state.completed.size}", strings.statTasksDone, Paper.green),
                    Triple("+${report?.earned ?: 0}", strings.statPointsEarned, Paper.ink),
                    Triple("${state.slips.size}", strings.statPenalties, Paper.danger),
                    Triple("${report?.endingBalance ?: 0}", strings.statBalance, Paper.accent),
                )
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    stats.chunked(2).forEach { rowStats ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowStats.forEach { (value, label, color) ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .paperCard(radius = 14)
                                        .padding(horizontal = 16.dp, vertical = 15.dp),
                                ) {
                                    Text(value, style = handStyle(30.sp, color))
                                    Text(
                                        label,
                                        Modifier.padding(top = 3.dp),
                                        style = sansStyle(12.sp, Paper.muted),
                                    )
                                }
                            }
                        }
                    }
                }

                EntrySection(strings.repCompleted, state.completed, Paper.green)
                EntrySection(strings.repObjectives, report?.achievedObjectives.orEmpty(), Paper.accent)
                EntrySection(strings.repSlips, state.slips, Paper.danger)
                EntrySection(strings.repRedemptions, state.redemptions, Paper.subtle)

                Column {
                    SectionLabel(strings.revJournal, Modifier.padding(bottom = 10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Paper.inset)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            state.journal.ifBlank { strings.repJournalEmpty },
                            style = sansStyle(
                                14.sp,
                                if (state.journal.isBlank()) Paper.muted else Paper.body,
                                lineHeight = 22.sp,
                            ),
                        )
                    }
                }

                if (state.isEmpty) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .dashedBorder(radius = 13)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(strings.repEmpty, style = sansStyle(13.sp, Paper.muted))
                    }
                }
            }
        }
    }
}

/** The badge that says out loud what the screen is: a week that can be read, not changed. */
@Composable
private fun ReadOnlyTag(strings: Strings) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Paper.inset)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            strings.repReadOnly.uppercase(),
            style = sansStyle(9.5.sp, Paper.subtle, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
        )
    }
}

/** One group of ledger entries — label on the left, its signed points on the right. */
@Composable
private fun EntrySection(title: String, entries: List<PointsEvent>, color: Color) {
    if (entries.isEmpty()) return
    Column {
        SectionLabel(title, Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            entries.forEach { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .paperCard(radius = 12)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        entry.label,
                        Modifier.weight(1f),
                        style = sansStyle(13.5.sp, Paper.ink, FontWeight.Medium),
                    )
                    Text(
                        if (entry.delta >= 0) "+${entry.delta}" else "${entry.delta}",
                        style = handStyle(16.sp, color),
                    )
                }
            }
        }
    }
}
