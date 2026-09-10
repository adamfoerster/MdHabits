package com.adamfoerster.mdhabits.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.GearIcon
import com.adamfoerster.mdhabits.ui.components.GlyphPlate
import com.adamfoerster.mdhabits.ui.components.HandCheckbox
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.PenaltyListSheet
import com.adamfoerster.mdhabits.ui.components.dashedBorder
import com.adamfoerster.mdhabits.ui.components.frequencyLabel
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.hardShadow
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRedeem: () -> Unit,
    onOpenReview: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val penalties by viewModel.penalties.collectAsStateWithLifecycle()
    var showPenalties by remember { mutableStateOf(false) }
    val toast = rememberToastState()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // Notebook margin: the thin terracotta line running down the page.
                .drawBehind {
                    val x = 34.dp.toPx()
                    drawLine(Paper.marginRed, Offset(x, 0f), Offset(x, size.height), 1.5.dp.toPx())
                },
        ) {
            Header(state, strings, onOpenTheme, onOpenSettings)

            // Penalty / redeem actions.
            Row(
                Modifier.padding(start = 46.dp, end = 20.dp, top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .hardShadow(Paper.shadowSoft, radius = 8, dy = 2f, dx = 2f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paper.card)
                        .border(1.5.dp, Paper.buttonBorder, RoundedCornerShape(8.dp))
                        .paperClick { showPenalties = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(strings.penaltyCta, style = sansStyle(13.sp, Paper.danger, FontWeight.Bold))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .hardShadow(Paper.shadow, radius = 8, dy = 2f, dx = 2f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Paper.ink)
                        .paperClick(onClick = onOpenRedeem),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${strings.redeemCta} · ${state.balance}",
                        style = sansStyle(13.sp, Paper.onDark, FontWeight.Bold),
                    )
                }
            }

            // Week task list.
            Column(Modifier.padding(start = 46.dp, end = 20.dp, top = 14.dp, bottom = 20.dp)) {
                val allTasks = state.allTasks
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(strings.tasksHand, Modifier.weight(1f), style = handStyle(20.sp, Paper.subtle))
                    Text(
                        strings.doneOf(allTasks.count { it.completed }, allTasks.size),
                        style = sansStyle(12.sp, Paper.faded, FontWeight.Medium),
                    )
                }

                if (allTasks.isEmpty() && !state.loading) {
                    Text(strings.emptyTasks, Modifier.padding(top = 14.dp), style = sansStyle(14.sp, Paper.muted))
                }
                val sections = listOf(
                    strings.todaySection to state.todayTasks,
                    strings.weeklySection to state.weeklyTasks,
                    strings.adhocSection to state.visibleAdhocTasks,
                ).filter { it.second.isNotEmpty() }
                sections.forEachIndexed { index, (label, rows) ->
                    TaskSectionLabel(label, topPadding = if (index == 0) 12 else 16)
                    rows.forEach { row -> TaskRow(row, strings) { toggled -> onToggle(viewModel, toast, strings, row, toggled) } }
                }

                // Weekly review CTA — only until the review creates this week's note.
                if (state.showReviewCta) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp)
                            .dashedBorder(radius = 12)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Paper.inset)
                            .paperClick(onClick = onOpenReview)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        GlyphPlate("✎", bg = Paper.card, size = 36.dp, glyphSize = 20.sp)
                        Column(Modifier.weight(1f)) {
                            Text(strings.weeklyReviewTitle, style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold))
                            Text(strings.weeklyReviewSub, style = sansStyle(12.sp, Paper.muted))
                        }
                        Chevron(left = false, color = Paper.accent, size = 18.dp)
                    }
                }
            }
        }

        PaperToast(toast)
    }

    if (showPenalties) {
        PenaltyListSheet(
            penalties = penalties,
            onDismiss = { showPenalties = false },
            onApply = { penalty ->
                viewModel.onApplyPenalty(penalty)
                showPenalties = false
                toast.show("−${penalty.pointCost} ${strings.pointsWord} · ${penalty.name}")
            },
        )
    }
}

private fun onToggle(
    viewModel: HomeViewModel,
    toast: com.adamfoerster.mdhabits.ui.components.ToastState,
    strings: Strings,
    row: HomeTaskRow,
    completed: Boolean,
) {
    viewModel.onToggleComplete(row.task, completed)
    if (completed) toast.show("+${row.task.points} ${strings.pointsWord} · ${row.task.title}")
}

@Composable
private fun Header(
    state: HomeUiState,
    strings: Strings,
    onOpenTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    Paper.divider,
                    Offset(0f, size.height - 1.dp.toPx()),
                    Offset(size.width, size.height - 1.dp.toPx()),
                    2.dp.toPx(),
                )
            }
            .padding(start = 46.dp, end = 20.dp, top = 6.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            // Hand-taped week badge, slightly rotated like in the design.
            Column(
                Modifier
                    .rotate(-1.5f)
                    .dashedBorder(color = Paper.badgeBorder, radius = 6)
                    .background(Paper.card, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "${strings.weekPrefix.uppercase()} ${state.weekNumber}",
                    style = sansStyle(13.sp, Paper.body, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
                )
                state.range?.let {
                    Text(formatRange(strings, it), style = sansStyle(11.5.sp, Paper.muted, FontWeight.Medium))
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Paper.card)
                    .border(1.5.dp, Paper.buttonBorder, RoundedCornerShape(8.dp))
                    .paperClick(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                GearIcon()
            }
        }
        Row(
            Modifier.padding(top = 12.dp).paperClick(onClick = onOpenTheme),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (state.hasTheme) state.themeName else strings.noTheme,
                style = handStyle(30.sp),
            )
            Text(
                strings.seeTheme,
                style = sansStyle(12.sp, Paper.accent, FontWeight.SemiBold)
                    .copy(textDecoration = TextDecoration.Underline),
            )
        }
    }
}

@Composable
private fun TaskSectionLabel(text: String, topPadding: Int = 12) {
    Text(
        text.uppercase(),
        Modifier.padding(top = topPadding.dp, bottom = 2.dp),
        style = sansStyle(10.sp, Paper.accent.copy(alpha = 0.7f), FontWeight.Bold).copy(letterSpacing = 1.4.sp),
    )
}

@Composable
private fun TaskRow(row: HomeTaskRow, strings: Strings, onToggle: (Boolean) -> Unit) {
    val done = row.completed
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    Paper.borderStrong,
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    1.5.dp.toPx(),
                )
            }
            .paperClick { onToggle(!done) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        HandCheckbox(checked = done)
        Column(Modifier.weight(1f)) {
            Text(
                row.task.title,
                style = sansStyle(15.sp, if (done) Paper.doneText else Paper.ink, FontWeight.Medium)
                    .copy(textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None),
            )
            val subtitle = (row.linkedNames + strings.frequencyLabel(row.task)).joinToString(" · ")
            Text(subtitle, style = sansStyle(11.sp, Paper.faded))
        }
        Text(
            "+${row.task.points}",
            style = handStyle(17.sp, if (done) Paper.donePoints else Paper.pointsTint),
        )
    }
}

private fun formatRange(strings: Strings, range: WeekRange): String =
    "${strings.shortDate(range.start.dayOfMonth, range.start.monthNumber)} – " +
        strings.shortDate(range.endInclusive.dayOfMonth, range.endInclusive.monthNumber)
