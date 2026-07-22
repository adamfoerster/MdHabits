package com.adamfoerster.mdhabits.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.ui.components.EntityFormInitial
import com.adamfoerster.mdhabits.ui.components.EntityFormSheet
import com.adamfoerster.mdhabits.ui.components.EntityKind
import com.adamfoerster.mdhabits.ui.components.frequencyLabel
import com.adamfoerster.mdhabits.ui.components.GlyphPlate
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.LinkOption
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.SelectChip
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

private enum class CadTab { TASKS, OBJECTIVES, VALUES, PENALTIES, REWARDS }

/** One rendered row of the cadastros list. */
private data class CadItem(
    val id: String,
    val glyph: String,
    val glyphBg: Color,
    val glyphColor: Color,
    val name: String,
    val meta: String,
    val pointsLabel: String,
    val pointsColor: Color,
    val handGlyph: Boolean = true,
)

@Composable
fun RegisterScreen(viewModel: RegisterViewModel = koinViewModel()) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CadTab.TASKS) }
    var showSheet by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val toast = rememberToastState()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
                Kicker(strings.tabRegister)
                Text(strings.cadTitle, Modifier.padding(top = 2.dp), style = handStyle(34.sp))
            }

            // Category chips.
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CadTab.entries.forEach { candidate ->
                    SelectChip(
                        label = tabLabel(strings, candidate),
                        selected = tab == candidate,
                    ) { tab = candidate }
                }
            }

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(tabHeading(strings, tab), Modifier.weight(1f), style = handStyle(20.sp, Paper.subtle))
                    Text(
                        strings.newItem,
                        Modifier.paperClick { showSheet = true },
                        style = sansStyle(13.sp, Paper.accent, FontWeight.Bold),
                    )
                }
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    itemsFor(strings, tab, state).forEach { item ->
                        CadItemRow(item) { editingId = item.id }
                    }
                }
            }
        }

        PaperToast(toast)
    }

    if (showSheet || editingId != null) {
        val kind = when (tab) {
            CadTab.TASKS -> EntityKind.TASK
            CadTab.OBJECTIVES -> EntityKind.OBJECTIVE
            CadTab.VALUES -> EntityKind.VALUE
            CadTab.PENALTIES -> EntityKind.PENALTY
            CadTab.REWARDS -> EntityKind.REWARD
        }
        val linkOptions = state.values.map { LinkOption(it.id, it.name, isValue = true) } +
            state.objectives.map { LinkOption(it.id, it.title, isValue = false) }
        val close = { showSheet = false; editingId = null }
        EntityFormSheet(
            kind = kind,
            initial = editingId?.let { initialFor(tab, state, it) },
            linkOptions = linkOptions,
            onDismiss = close,
            onSave = { name, points, recurrence, daysOfWeek, linkIds ->
                val valueIds = linkIds.filter { id -> state.values.any { it.id == id } }
                val objectiveIds = linkIds.filter { id -> state.objectives.any { it.id == id } }
                val id = editingId
                when (kind) {
                    EntityKind.VALUE ->
                        if (id != null) viewModel.updateValue(id, name) else viewModel.addValue(name)
                    EntityKind.OBJECTIVE ->
                        if (id != null) viewModel.updateObjective(id, name, points) else viewModel.addObjective(name, points)
                    EntityKind.TASK ->
                        if (id != null) {
                            viewModel.updateTask(id, name, points, recurrence, daysOfWeek, valueIds, objectiveIds)
                        } else {
                            viewModel.addTask(name, points, recurrence, daysOfWeek, valueIds, objectiveIds)
                        }
                    EntityKind.PENALTY ->
                        if (id != null) viewModel.updatePenalty(id, name, points) else viewModel.addPenalty(name, points)
                    EntityKind.REWARD ->
                        if (id != null) viewModel.updateReward(id, name, points) else viewModel.addReward(name, points)
                }
                close()
                toast.show(strings.saved)
            },
        )
    }
}

/** The stored field values of the entity being edited, or null if it no longer exists. */
private fun initialFor(tab: CadTab, state: RegisterUiState, id: String): EntityFormInitial? = when (tab) {
    CadTab.TASKS -> state.tasks.find { it.id == id }?.let {
        EntityFormInitial(
            name = it.title,
            points = it.points,
            recurrence = it.recurrence,
            daysOfWeek = it.daysOfWeek,
            linkIds = it.linkedValueIds + it.linkedObjectiveIds,
        )
    }
    CadTab.OBJECTIVES -> state.objectives.find { it.id == id }?.let { EntityFormInitial(it.title, it.points) }
    CadTab.VALUES -> state.values.find { it.id == id }?.let { EntityFormInitial(it.name) }
    CadTab.PENALTIES -> state.penalties.find { it.id == id }?.let { EntityFormInitial(it.name, it.pointCost) }
    CadTab.REWARDS -> state.rewards.find { it.id == id }?.let { EntityFormInitial(it.name, it.pointCost) }
}

@Composable
private fun CadItemRow(item: CadItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().paperCard(radius = 13).paperClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphPlate(item.glyph, bg = item.glyphBg, color = item.glyphColor, hand = item.handGlyph, glyphSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text(item.name, style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold))
            Text(item.meta, style = sansStyle(11.5.sp, Paper.muted))
        }
        if (item.pointsLabel.isNotEmpty()) {
            Text(item.pointsLabel, style = sansStyle(14.sp, item.pointsColor, FontWeight.Bold))
        }
    }
}

private fun tabLabel(strings: Strings, tab: CadTab): String = when (tab) {
    CadTab.TASKS -> strings.cadTasks
    CadTab.OBJECTIVES -> strings.cadObjectives
    CadTab.VALUES -> strings.cadValues
    CadTab.PENALTIES -> strings.cadPenalties
    CadTab.REWARDS -> strings.cadRewards
}

private fun tabHeading(strings: Strings, tab: CadTab): String = when (tab) {
    CadTab.TASKS -> strings.cadHeadingTasks
    CadTab.OBJECTIVES -> strings.cadHeadingObjectives
    CadTab.VALUES -> strings.cadHeadingValues
    CadTab.PENALTIES -> strings.cadHeadingPenalties
    CadTab.REWARDS -> strings.cadHeadingRewards
}

private fun itemsFor(strings: Strings, tab: CadTab, state: RegisterUiState): List<CadItem> = when (tab) {
    CadTab.TASKS -> state.tasks.map { task ->
        CadItem(
            id = task.id,
            glyph = "✓", glyphBg = Paper.greenSoft, glyphColor = Paper.green,
            name = task.title,
            meta = (state.linkedNames(task) + strings.frequencyLabel(task)).joinToString(" · "),
            pointsLabel = "+${task.points}", pointsColor = Paper.subtle,
        )
    }
    CadTab.OBJECTIVES -> state.objectives.mapIndexed { index, objective ->
        CadItem(
            id = objective.id,
            glyph = (index + 1).toString(), glyphBg = Paper.claySoft, glyphColor = Paper.accent,
            name = objective.title, meta = strings.metaObjective(index + 1),
            pointsLabel = "+${objective.points}", pointsColor = Paper.subtle,
            handGlyph = false,
        )
    }
    CadTab.VALUES -> state.values.map { value ->
        CadItem(
            id = value.id,
            glyph = value.name.take(1), glyphBg = Paper.inset, glyphColor = Paper.subtle,
            name = value.name, meta = strings.metaValue,
            pointsLabel = "", pointsColor = Paper.muted,
        )
    }
    CadTab.PENALTIES -> state.penalties.map { penalty ->
        CadItem(
            id = penalty.id,
            glyph = "−", glyphBg = Paper.dangerSoft, glyphColor = Paper.danger,
            name = penalty.name, meta = strings.metaPenalty,
            pointsLabel = "−${penalty.pointCost}", pointsColor = Paper.danger,
            handGlyph = false,
        )
    }
    CadTab.REWARDS -> state.rewards.map { reward ->
        CadItem(
            id = reward.id,
            glyph = "★", glyphBg = Paper.inset, glyphColor = Paper.accent,
            name = reward.name, meta = strings.metaReward,
            pointsLabel = "${reward.pointCost} ${strings.pointsSuffix}", pointsColor = Paper.subtle,
        )
    }
}
