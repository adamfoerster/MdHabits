package com.adamfoerster.mdhabits.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.ui.components.BackSquareButton
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.CommitCheckbox
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.PaperTextField
import com.adamfoerster.mdhabits.ui.components.PrimaryButton
import com.adamfoerster.mdhabits.ui.components.SectionLabel
import com.adamfoerster.mdhabits.ui.components.dashedBorder
import com.adamfoerster.mdhabits.ui.components.frequencyLabel
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journal by viewModel.journal.collectAsStateWithLifecycle()
    val proximity by viewModel.proximity.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Back link.
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

            // Header with two-step progress.
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)) {
                Kicker(strings.revKicker(viewModel.reviewWeekNumber))
                Text(
                    if (state.step == 1) strings.revTitle1 else strings.revTitle2,
                    Modifier.padding(top = 2.dp),
                    style = handStyle(34.sp),
                )
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepBar(Paper.accent, Modifier.weight(1f))
                    StepBar(
                        if (state.step == 2) Paper.accent else Paper.marginRed.copy(alpha = 0.22f),
                        Modifier.weight(1f),
                    )
                }
                Text(
                    if (state.step == 1) strings.revStep1 else strings.revStep2,
                    Modifier.padding(top = 6.dp),
                    style = sansStyle(11.5.sp, Paper.muted, FontWeight.Medium),
                )
            }

            if (state.step == 1) {
                RetrospectiveStep(strings, state, journal, proximity, viewModel)
            } else {
                CommitStep(strings, state, viewModel, onBack)
            }
        }
    }
}

@Composable
private fun StepBar(color: Color, modifier: Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(color))
}

@Composable
private fun RetrospectiveStep(
    strings: Strings,
    state: ReviewUiState,
    journal: String,
    proximity: Int,
    viewModel: ReviewViewModel,
) {
    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Report stats 2x2.
        Column {
            SectionLabel(strings.revReport, Modifier.padding(bottom = 10.dp))
            val report = state.report
            val stats = listOf(
                Triple("${report?.completedTasks?.size ?: state.completedCount}/${state.tasks.size}", strings.statTasksDone, Paper.green),
                Triple("+${report?.earned ?: 0}", strings.statPointsEarned, Paper.ink),
                Triple("${report?.penalties?.size ?: 0}", strings.statPenalties, Paper.danger),
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
                                Text(label, Modifier.padding(top = 3.dp), style = sansStyle(12.sp, Paper.muted))
                            }
                        }
                    }
                }
            }
        }

        // Journal.
        Column {
            SectionLabel(strings.revJournal, Modifier.padding(bottom = 10.dp))
            PaperTextField(
                value = journal,
                onValueChange = viewModel::setJournal,
                placeholder = strings.revJournalPh,
                bold = false,
                singleLine = false,
                minHeight = 130.dp,
            )
        }

        // Proximity 1..5.
        Column {
            SectionLabel(strings.revProximityQ, Modifier.padding(bottom = 4.dp))
            Text(
                strings.proxLabels[proximity - 1],
                Modifier.padding(bottom = 12.dp),
                style = sansStyle(13.sp, Paper.muted),
            )
            val icons = listOf("↓↓", "↓", "→", "↑", "↑↑")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                icons.forEachIndexed { index, icon ->
                    val selected = proximity == index + 1
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) Paper.ink else Paper.card)
                            .border(
                                1.5.dp,
                                if (selected) Paper.ink else Paper.borderStrong,
                                RoundedCornerShape(11.dp),
                            )
                            .paperClick { viewModel.setProximity(index + 1) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            icon,
                            style = sansStyle(16.sp, if (selected) Paper.onDark else Paper.muted, FontWeight.Bold),
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(strings.revPlanNext, Modifier.weight(1f)) { viewModel.next() }
        }
    }
}

@Composable
private fun CommitStep(
    strings: Strings,
    state: ReviewUiState,
    viewModel: ReviewViewModel,
    onBack: () -> Unit,
) {
    val range = viewModel.planWeekRange
    val rangeLabel = "${strings.shortDate(range.start.dayOfMonth, range.start.monthNumber)} – " +
        strings.shortDate(range.endInclusive.dayOfMonth, range.endInclusive.monthNumber)

    Column(
        Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            strings.revCommitIntro(viewModel.planWeekNumber, rangeLabel),
            style = sansStyle(14.sp, Paper.body, lineHeight = 22.sp),
        )

        val groups = listOf(
            strings.recurrentSection to state.tasks.filter { it.recurrence != Recurrence.ADHOC },
            strings.adhocSection to state.tasks.filter { it.recurrence == Recurrence.ADHOC },
        ).filter { it.second.isNotEmpty() }

        groups.forEach { (title, tasks) ->
            Column {
                SectionLabel(title, Modifier.padding(bottom = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    tasks.forEach { task ->
                        CommitRow(
                            task = task,
                            selected = task.id in state.commitIds,
                            recurrenceLabel = strings.frequencyLabel(task),
                        ) { viewModel.toggleCommit(task.id) }
                    }
                }
            }
        }

        // Summary.
        val committed = state.tasks.filter { it.id in state.commitIds }
        Box(
            Modifier
                .fillMaxWidth()
                .dashedBorder(radius = 13)
                .clip(RoundedCornerShape(13.dp))
                .background(Paper.inset)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                strings.revCommitSummary(committed.size, committed.sumOf { it.points }),
                style = sansStyle(14.sp, Paper.ink, FontWeight.SemiBold),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BackSquareButton { viewModel.backToStep1() }
            PrimaryButton(strings.revStartWeek, Modifier.weight(1f)) {
                viewModel.submit(onDone = onBack)
            }
        }
    }
}

@Composable
private fun CommitRow(task: Task, selected: Boolean, recurrenceLabel: String, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Paper.inset else Paper.card)
            .border(
                1.5.dp,
                if (selected) Paper.selectedBorder else Paper.border,
                RoundedCornerShape(13.dp),
            )
            .paperClick(onClick = onToggle)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CommitCheckbox(checked = selected)
        Column(Modifier.weight(1f)) {
            Text(task.title, style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold))
            Text(recurrenceLabel, style = sansStyle(11.5.sp, Paper.muted))
        }
        Text("+${task.points}", style = handStyle(16.sp, Paper.subtle))
    }
}
