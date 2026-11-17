package com.adamfoerster.mdhabits.ui.screens.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.CloseIcon
import com.adamfoerster.mdhabits.ui.components.EntityFormInitial
import com.adamfoerster.mdhabits.ui.components.EntityFormSheet
import com.adamfoerster.mdhabits.ui.components.EntityKind
import com.adamfoerster.mdhabits.ui.components.GiftBoxIcon
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.LinkOption
import com.adamfoerster.mdhabits.ui.components.MinusIcon
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.PlusIcon
import com.adamfoerster.mdhabits.ui.components.SearchIcon
import com.adamfoerster.mdhabits.ui.components.TargetIcon
import com.adamfoerster.mdhabits.ui.components.TaskBoxIcon
import com.adamfoerster.mdhabits.ui.components.TickIcon
import com.adamfoerster.mdhabits.ui.components.ValueStarIcon
import com.adamfoerster.mdhabits.ui.components.WarningIcon
import com.adamfoerster.mdhabits.ui.components.dragHorizontalScroll
import com.adamfoerster.mdhabits.ui.components.frequencyLabel
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private enum class CadTab { TASKS, OBJECTIVES, VALUES, PENALTIES, REWARDS }

/** One rendered row of the cadastros list. */
private data class CadItem(
    val id: String,
    val name: String,
    val meta: String,
    val pointsLabel: String,
    val pointsColor: Color,
)

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel = koinViewModel(),
    // Health goals only exist where the platform has a health store (Health Connect on Android).
    healthSupported: Boolean = koinInject<HealthDataSource>().isSupported,
    reminderScheduler: ReminderScheduler = koinInject(),
) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(CadTab.TASKS) }
    var taskFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var query by remember { mutableStateOf("") }
    var showSheet by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    val toast = rememberToastState()

    val plural = tabLabel(strings, tab).lowercase()
    val matchingTasks = state.searchTasks(query)
    val items = itemsFor(strings, tab, state, query, matchingTasks.filter(taskFilter::matches))
    val total = totalFor(tab, state)

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
                Kicker(strings.tabRegister, color = Paper.subtle)
                Text(strings.cadTitle, Modifier.padding(top = 2.dp), style = handStyle(38.sp, weight = FontWeight.Bold))
            }

            CadTabSelector(
                strings = strings,
                selected = tab,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            ) {
                tab = it
                taskFilter = TaskFilter.ALL
            }

            SearchField(
                query = query,
                placeholder = strings.cad.search(plural),
                clearLabel = strings.cad.clearSearch,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                onQueryChange = { query = it },
            )

            if (tab == CadTab.TASKS) {
                val counts = state.filterCounts(matchingTasks)
                Row(
                    Modifier
                        .dragHorizontalScroll()
                        .padding(horizontal = 20.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskFilter.entries.forEach { candidate ->
                        FilterChip(
                            label = filterLabel(strings, candidate),
                            count = counts[candidate] ?: 0,
                            selected = taskFilter == candidate,
                        ) { taskFilter = candidate }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    showingText(strings.cad.showing(items.size, total, plural), items.size),
                    Modifier.weight(1f),
                    style = sansStyle(13.5.sp, Paper.body, FontWeight.SemiBold),
                )
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Paper.ink)
                        .paperClick { showSheet = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PlusIcon(Paper.onDark)
                    Text(newLabel(strings, tab), style = sansStyle(14.5.sp, Paper.onDark, FontWeight.Bold), maxLines = 1)
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    CadItemRow(tab, item) { editingId = item.id }
                }
                if (items.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            strings = strings,
                            message = when {
                                query.isNotBlank() -> strings.cad.emptyQuery(query.trim())
                                tab == CadTab.TASKS && taskFilter != TaskFilter.ALL -> strings.cad.emptyFilter
                                else -> strings.cad.emptyList
                            },
                            showReset = query.isNotBlank() || taskFilter != TaskFilter.ALL,
                        ) {
                            query = ""
                            taskFilter = TaskFilter.ALL
                        }
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
            healthSupported = healthSupported,
            remindersSupported = reminderScheduler.isSupported,
            onRequestReminderPermission = reminderScheduler::requestPermission,
            onDismiss = close,
            onDelete = editingId?.takeIf { kind == EntityKind.TASK }?.let { id ->
                {
                    viewModel.deleteTask(id)
                    close()
                }
            },
            onSave = { name, points, recurrence, daysOfWeek, linkIds, healthGoal, reminder ->
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
                            viewModel.updateTask(id, name, points, recurrence, daysOfWeek, valueIds, objectiveIds, healthGoal, reminder)
                        } else {
                            viewModel.addTask(name, points, recurrence, daysOfWeek, valueIds, objectiveIds, healthGoal, reminder)
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
            healthGoal = it.healthGoal,
            reminder = it.reminder,
        )
    }
    CadTab.OBJECTIVES -> state.objectives.find { it.id == id }?.let { EntityFormInitial(it.title, it.points) }
    CadTab.VALUES -> state.values.find { it.id == id }?.let { EntityFormInitial(it.name) }
    CadTab.PENALTIES -> state.penalties.find { it.id == id }?.let { EntityFormInitial(it.name, it.pointCost) }
    CadTab.REWARDS -> state.rewards.find { it.id == id }?.let { EntityFormInitial(it.name, it.pointCost) }
}

/** The five entity types as a segmented control: an icon over each label, the chosen one inked. */
@Composable
private fun CadTabSelector(
    strings: Strings,
    selected: CadTab,
    modifier: Modifier = Modifier,
    onSelect: (CadTab) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Paper.segmentTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CadTab.entries.forEach { candidate ->
            val on = candidate == selected
            val color = if (on) Paper.onDark else Paper.body
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Paper.ink else Color.Transparent)
                    .paperClick { onSelect(candidate) }
                    .semantics { role = Role.Tab; this.selected = on }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                when (candidate) {
                    CadTab.TASKS -> TaskBoxIcon(color)
                    CadTab.OBJECTIVES -> TargetIcon(color)
                    CadTab.VALUES -> ValueStarIcon(color)
                    CadTab.PENALTIES -> WarningIcon(color)
                    CadTab.REWARDS -> GiftBoxIcon(color)
                }
                Text(
                    tabLabel(strings, candidate),
                    Modifier.padding(horizontal = 3.dp),
                    style = sansStyle(10.5.sp, color, FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    clearLabel: String,
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val textStyle = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        textStyle = textStyle,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (focused) Color.White else Paper.card)
                    .border(1.5.dp, if (focused) Paper.ink else Paper.borderStrong, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SearchIcon(Paper.subtle)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, style = textStyle.copy(color = Paper.muted), maxLines = 1)
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Paper.inset)
                            .paperClick { onQueryChange("") }
                            .semantics { contentDescription = clearLabel; role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        CloseIcon(Paper.body)
                    }
                }
            }
        },
    )
}

/** Frequency filter chip with the number of tasks it would show. */
@Composable
private fun FilterChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.5.dp, if (selected) Paper.ink else Paper.buttonBorder, RoundedCornerShape(10.dp))
            .paperClick(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = sansStyle(13.5.sp, if (selected) Paper.ink else Paper.body, FontWeight.Bold))
        Text(count.toString(), style = sansStyle(12.sp, Paper.subtle, FontWeight.Bold))
    }
}

@Composable
private fun CadItemRow(tab: CadTab, item: CadItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().paperCard(radius = 14).paperClick(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val (plateBg, plateInk) = plateColors(tab)
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(plateBg),
            contentAlignment = Alignment.Center,
        ) {
            when (tab) {
                CadTab.TASKS -> TickIcon(plateInk)
                CadTab.OBJECTIVES -> TargetIcon(plateInk, size = 18.dp, width = 2f, dot = false)
                CadTab.VALUES -> ValueStarIcon(plateInk, size = 18.dp, width = 2f)
                CadTab.PENALTIES -> MinusIcon(plateInk)
                CadTab.REWARDS -> GiftBoxIcon(plateInk, size = 18.dp, width = 2f, bow = false)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = sansStyle(15.5.sp, Paper.ink, FontWeight.Bold, lineHeight = 19.sp))
            if (item.meta.isNotEmpty()) {
                Text(item.meta, style = sansStyle(12.5.sp, Paper.subtle, FontWeight.SemiBold, lineHeight = 16.sp))
            }
        }
        if (item.pointsLabel.isNotEmpty()) {
            Text(item.pointsLabel, style = sansStyle(15.sp, item.pointsColor, FontWeight.Bold), maxLines = 1)
        }
        Chevron(left = false, color = Paper.faded, size = 16.dp)
    }
}

@Composable
private fun EmptyState(strings: Strings, message: String, showReset: Boolean, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(strings.cad.emptyTitle, style = handStyle(28.sp, weight = FontWeight.Bold))
        Text(message, style = sansStyle(14.sp, Paper.body, FontWeight.SemiBold), textAlign = TextAlign.Center)
        if (showReset) {
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, Paper.ink, RoundedCornerShape(14.dp))
                    .paperClick(onClick = onReset)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(strings.cad.resetAll, style = sansStyle(14.sp, Paper.ink, FontWeight.Bold))
            }
        }
    }
}

/** "Showing 3 of 12 tasks" with the shown count emphasised. */
private fun showingText(text: String, shown: Int): AnnotatedString = buildAnnotatedString {
    append(text)
    val start = text.indexOf(shown.toString())
    if (start >= 0) {
        addStyle(SpanStyle(color = Paper.ink, fontWeight = FontWeight.ExtraBold), start, start + shown.toString().length)
    }
}

private fun plateColors(tab: CadTab): Pair<Color, Color> = when (tab) {
    CadTab.TASKS -> Paper.greenSoft to Paper.green
    CadTab.OBJECTIVES -> Paper.ochreSoft to Paper.ochre
    CadTab.VALUES -> Paper.slateSoft to Paper.slate
    CadTab.PENALTIES -> Paper.dangerSoft to Paper.danger
    CadTab.REWARDS -> Paper.amberSoft to Paper.amber
}

private fun tabLabel(strings: Strings, tab: CadTab): String = when (tab) {
    CadTab.TASKS -> strings.cadTasks
    CadTab.OBJECTIVES -> strings.cadObjectives
    CadTab.VALUES -> strings.cadValues
    CadTab.PENALTIES -> strings.cadPenalties
    CadTab.REWARDS -> strings.cadRewards
}

private fun newLabel(strings: Strings, tab: CadTab): String = when (tab) {
    CadTab.TASKS -> strings.cad.newTask
    CadTab.OBJECTIVES -> strings.cad.newObjective
    CadTab.VALUES -> strings.cad.newValue
    CadTab.PENALTIES -> strings.cad.newPenalty
    CadTab.REWARDS -> strings.cad.newReward
}

private fun filterLabel(strings: Strings, filter: TaskFilter) = when (filter) {
    TaskFilter.ALL -> strings.filterAll
    TaskFilter.ADHOC -> strings.recurAdhoc
    TaskFilter.WEEKLY -> strings.recurWeekly
    TaskFilter.DAILY -> strings.recurDaily
    TaskFilter.DAYS_OF_WEEK -> strings.recurDaysOfWeek
    TaskFilter.HABIT -> strings.recurHabit
}

private fun totalFor(tab: CadTab, state: RegisterUiState): Int = when (tab) {
    CadTab.TASKS -> state.tasks.size
    CadTab.OBJECTIVES -> state.objectives.size
    CadTab.VALUES -> state.values.size
    CadTab.PENALTIES -> state.penalties.size
    CadTab.REWARDS -> state.rewards.size
}

/** The rows of [tab] matching [query]; for tasks, [tasks] is already searched and filtered. */
private fun itemsFor(
    strings: Strings,
    tab: CadTab,
    state: RegisterUiState,
    query: String,
    tasks: List<Task>,
): List<CadItem> {
    val rows = when (tab) {
        CadTab.TASKS -> return tasks.map { task ->
            CadItem(
                id = task.id,
                name = task.title,
                meta = (
                    state.linkedNames(task) + strings.frequencyLabel(task) +
                        listOfNotNull(task.reminder?.let { "⏰ $it" })
                    ).joinToString(" · "),
                pointsLabel = "+${task.points}", pointsColor = Paper.ink,
            )
        }
        CadTab.OBJECTIVES -> state.objectives.map { objective ->
            CadItem(
                id = objective.id,
                name = objective.title,
                meta = strings.cad.linkedTasks(state.tasksLinkedToObjective(objective.id)),
                pointsLabel = "+${objective.points}", pointsColor = Paper.ink,
            )
        }
        CadTab.VALUES -> state.values.map { value ->
            CadItem(
                id = value.id,
                name = value.name,
                meta = strings.cad.linkedTasks(state.tasksLinkedToValue(value.id)),
                pointsLabel = "", pointsColor = Paper.ink,
            )
        }
        CadTab.PENALTIES -> state.penalties.map { penalty ->
            CadItem(
                id = penalty.id,
                name = penalty.name, meta = strings.metaPenalty,
                pointsLabel = "−${penalty.pointCost}", pointsColor = Paper.danger,
            )
        }
        CadTab.REWARDS -> state.rewards.map { reward ->
            CadItem(
                id = reward.id,
                name = reward.name, meta = strings.metaReward,
                pointsLabel = "${reward.pointCost} ${strings.pointsSuffix}", pointsColor = Paper.ink,
            )
        }
    }
    return rows.filter { matchesSearch(query, listOf(it.name)) }
}

