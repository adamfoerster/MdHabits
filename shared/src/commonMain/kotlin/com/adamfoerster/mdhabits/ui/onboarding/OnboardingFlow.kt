package com.adamfoerster.mdhabits.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.ui.components.BackSquareButton
import com.adamfoerster.mdhabits.ui.components.CheckMark
import com.adamfoerster.mdhabits.ui.components.DashedAddButton
import com.adamfoerster.mdhabits.ui.components.EntityFormSheet
import com.adamfoerster.mdhabits.ui.components.EntityKind
import com.adamfoerster.mdhabits.ui.components.frequencyLabel
import com.adamfoerster.mdhabits.ui.components.FolderIcon
import com.adamfoerster.mdhabits.ui.components.GlyphPlate
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.LanguageSelector
import com.adamfoerster.mdhabits.ui.components.LinkOption
import com.adamfoerster.mdhabits.ui.components.PaperTextField
import com.adamfoerster.mdhabits.ui.components.PrimaryButton
import com.adamfoerster.mdhabits.ui.components.SectionLabel
import com.adamfoerster.mdhabits.ui.components.dashedBorder
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

private const val STEP_COUNT = 6

@Composable
fun OnboardingFlow(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(0) }
    var sheetKind by remember { mutableStateOf<EntityKind?>(null) }

    if (draft.finished) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    val canAdvance = when (step) {
        0 -> true
        1 -> draft.values.size >= 2
        2 -> draft.themeName.isNotBlank() && draft.objectives.size == 3
        3 -> (draft.tasks.size + draft.penalties.size) >= 3
        4 -> draft.rewards.size >= 2
        else -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Step dots + kicker.
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(STEP_COUNT) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (index <= step) Paper.accent else Paper.marginRed.copy(alpha = 0.22f)),
                    )
                }
            }
            Kicker(strings.onbKickers[step], Modifier.padding(top = 12.dp))
        }

        // Step content.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 26.dp),
        ) {
            when (step) {
                0 -> WelcomeStep(viewModel, draft)
                1 -> ValuesStep(viewModel, draft) { sheetKind = EntityKind.VALUE }
                2 -> ThemeStep(viewModel, draft) { sheetKind = EntityKind.OBJECTIVE }
                3 -> TasksStep(viewModel, draft, onAddTask = { sheetKind = EntityKind.TASK }, onAddPenalty = { sheetKind = EntityKind.PENALTY })
                4 -> RewardsStep(viewModel, draft) { sheetKind = EntityKind.REWARD }
                5 -> DoneStep()
            }
        }

        // Bottom navigation.
        Row(
            Modifier.padding(start = 26.dp, end = 26.dp, top = 14.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // A picked folder that already holds an annual theme is an existing vault: the setup
            // steps would only duplicate its notes, so the flow jumps straight to the last step.
            if (step > 0) {
                BackSquareButton { step = if (draft.vaultHasTheme) 0 else step - 1 }
            }
            PrimaryButton(
                text = if (step == STEP_COUNT - 1) strings.onbEnterApp else strings.onbContinue,
                modifier = Modifier.weight(1f),
                enabled = canAdvance,
            ) {
                when {
                    step == STEP_COUNT - 1 -> viewModel.finish()
                    draft.vaultHasTheme -> step = STEP_COUNT - 1
                    else -> step++
                }
            }
        }
    }

    sheetKind?.let { kind ->
        val linkOptions = draft.values.map { LinkOption(it.id, it.name, isValue = true) } +
            draft.objectives.map { LinkOption(it.id, it.title, isValue = false) }
        EntityFormSheet(
            kind = kind,
            linkOptions = linkOptions,
            onDismiss = { sheetKind = null },
            onSave = { name, points, recurrence, daysOfWeek, linkIds ->
                when (kind) {
                    EntityKind.VALUE -> viewModel.addValue(name, "")
                    EntityKind.OBJECTIVE -> viewModel.addObjective(name, points)
                    EntityKind.TASK -> viewModel.addTask(
                        title = name,
                        points = points,
                        recurrence = recurrence,
                        daysOfWeek = daysOfWeek,
                        valueIds = linkIds.filter { id -> draft.values.any { it.id == id } },
                        objectiveIds = linkIds.filter { id -> draft.objectives.any { it.id == id } },
                    )
                    EntityKind.PENALTY -> viewModel.addPenalty(name, points)
                    EntityKind.REWARD -> viewModel.addReward(name, points)
                }
                sheetKind = null
            },
        )
    }
}

/* ------------------------------------------------------------------ steps */

@Composable
private fun WelcomeStep(viewModel: OnboardingViewModel, draft: OnboardingDraft) {
    val strings = LocalStrings.current
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth()) {
        Text(strings.onbWelcomeTitle, Modifier.padding(top = 6.dp), style = handStyle(40.sp))
        Text(
            strings.onbWelcomeBody,
            Modifier.padding(top = 8.dp),
            style = sansStyle(15.sp, Paper.body, lineHeight = 23.sp),
        )
        Spacer(Modifier.height(28.dp))
        SectionLabel(strings.cfgLanguage, Modifier.padding(bottom = 8.dp))
        LanguageSelector(selected = lang, onSelect = viewModel::setLanguage)
        Spacer(Modifier.height(20.dp))
        SectionLabel(strings.onbWhereData, Modifier.padding(bottom = 8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .dashedBorder(radius = 14)
                .clip(RoundedCornerShape(14.dp))
                .background(Paper.card)
                .paperClick { viewModel.pickFolder() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Paper.inset),
                contentAlignment = Alignment.Center,
            ) {
                FolderIcon(size = 22.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    draft.vault?.displayName ?: strings.onbChooseFolder,
                    style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold),
                )
                Text(
                    when {
                        draft.vaultHasTheme -> strings.onbExistingVaultHint
                        draft.vault != null -> strings.onbFolderSavedHint
                        else -> strings.onbFolderTapHint
                    },
                    style = sansStyle(12.sp, Paper.muted),
                )
            }
            Text(
                if (draft.vault != null) strings.onbSwap else strings.onbOpen,
                style = sansStyle(13.sp, Paper.accent, FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun ValuesStep(viewModel: OnboardingViewModel, draft: OnboardingDraft, onAdd: () -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(strings.onbValuesTitle, Modifier.padding(top = 6.dp), style = handStyle(36.sp))
        Text(
            strings.onbValuesBody,
            Modifier.padding(top = 6.dp, bottom = 16.dp),
            style = sansStyle(14.sp, Paper.body, lineHeight = 21.sp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            draft.values.forEach { value ->
                Row(
                    Modifier.fillMaxWidth().paperCard(radius = 12).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphPlate(value.name.take(1), size = 30.dp, glyphSize = 16.sp, color = Paper.subtle)
                    Text(value.name, Modifier.weight(1f), style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold))
                    Text(
                        strings.remove,
                        Modifier.paperClick { viewModel.removeValue(value.id) },
                        style = sansStyle(13.sp, Paper.faded),
                    )
                }
            }
        }
        DashedAddButton(strings.addValueCta, Modifier.padding(top = 12.dp), onClick = onAdd)
    }
}

@Composable
private fun ThemeStep(viewModel: OnboardingViewModel, draft: OnboardingDraft, onAddObjective: () -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(strings.onbThemeTitle, Modifier.padding(top = 6.dp), style = handStyle(36.sp))
        Text(
            strings.onbThemeBody,
            Modifier.padding(top = 6.dp, bottom = 14.dp),
            style = sansStyle(14.sp, Paper.body, lineHeight = 21.sp),
        )
        SectionLabel(strings.onbThemeName, Modifier.padding(bottom = 5.dp))
        PaperTextField(
            value = draft.themeName,
            onValueChange = viewModel::setThemeName,
            placeholder = strings.onbThemeNamePh,
        )
        Spacer(Modifier.height(12.dp))
        SectionLabel(strings.onbThemeDescription, Modifier.padding(bottom = 5.dp))
        PaperTextField(
            value = draft.themeDescription,
            onValueChange = viewModel::setThemeDescription,
            placeholder = strings.onbThemeDescPh,
            bold = false,
            singleLine = false,
            minHeight = 96.dp,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel(strings.onbObjectivesTitle, Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            draft.objectives.forEachIndexed { index, objective ->
                Row(
                    Modifier.fillMaxWidth().paperCard(radius = 12).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NumberCircle(index + 1)
                    Text(objective.title, Modifier.weight(1f), style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold))
                    Text("+${objective.points}", style = sansStyle(13.sp, Paper.subtle, FontWeight.Bold))
                    Text(
                        strings.remove,
                        Modifier.paperClick { viewModel.removeObjective(objective.id) },
                        style = sansStyle(13.sp, Paper.faded),
                    )
                }
            }
        }
        if (draft.objectives.size < 3) {
            DashedAddButton("+ ${strings.sheetObjectiveTitle}", Modifier.padding(top = 12.dp), onClick = onAddObjective)
        }
    }
}

@Composable
private fun TasksStep(
    viewModel: OnboardingViewModel,
    draft: OnboardingDraft,
    onAddTask: () -> Unit,
    onAddPenalty: () -> Unit,
) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(strings.onbTasksTitle, Modifier.padding(top = 6.dp), style = handStyle(36.sp))
        Text(
            strings.onbTasksBody,
            Modifier.padding(top = 6.dp, bottom = 14.dp),
            style = sansStyle(14.sp, Paper.body, lineHeight = 21.sp),
        )
        SectionLabel(strings.cadTasks, Modifier.padding(bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.tasks.forEach { task ->
                Row(
                    Modifier.fillMaxWidth().paperCard(radius = 12).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(task.title, Modifier.weight(1f), style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold))
                    Text(
                        strings.frequencyLabel(task),
                        style = sansStyle(11.sp, Paper.faded),
                    )
                    Text("+${task.points}", style = sansStyle(13.sp, Paper.green, FontWeight.Bold))
                    Text(
                        strings.remove,
                        Modifier.paperClick { viewModel.removeTask(task.id) },
                        style = sansStyle(13.sp, Paper.faded),
                    )
                }
            }
        }
        DashedAddButton("+ ${strings.sheetTaskTitle}", Modifier.padding(top = 10.dp), onClick = onAddTask)
        SectionLabel(strings.onbPenaltiesTitle, Modifier.padding(top = 16.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.penalties.forEach { penalty ->
                Row(
                    Modifier.fillMaxWidth().paperCard(radius = 12).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(penalty.name, Modifier.weight(1f), style = sansStyle(14.5.sp, Paper.ink, FontWeight.SemiBold))
                    Text("−${penalty.pointCost}", style = sansStyle(13.sp, Paper.danger, FontWeight.Bold))
                    Text(
                        strings.remove,
                        Modifier.paperClick { viewModel.removePenalty(penalty.id) },
                        style = sansStyle(13.sp, Paper.faded),
                    )
                }
            }
        }
        DashedAddButton("+ ${strings.sheetPenaltyTitle}", Modifier.padding(top = 10.dp), onClick = onAddPenalty)
    }
}

@Composable
private fun RewardsStep(viewModel: OnboardingViewModel, draft: OnboardingDraft, onAdd: () -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth()) {
        Text(strings.onbRewardsTitle, Modifier.padding(top = 6.dp), style = handStyle(36.sp))
        Text(
            strings.onbRewardsBody,
            Modifier.padding(top = 6.dp, bottom = 14.dp),
            style = sansStyle(14.sp, Paper.body, lineHeight = 21.sp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            draft.rewards.forEach { reward ->
                Row(
                    Modifier.fillMaxWidth().paperCard(radius = 12).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GlyphPlate("★", glyphSize = 18.sp)
                    Text(reward.name, Modifier.weight(1f), style = sansStyle(15.sp, Paper.ink, FontWeight.SemiBold))
                    Text("${reward.pointCost} ${strings.pointsSuffix}", style = sansStyle(14.sp, Paper.subtle, FontWeight.Bold))
                    Text(
                        strings.remove,
                        Modifier.paperClick { viewModel.removeReward(reward.id) },
                        style = sansStyle(13.sp, Paper.faded),
                    )
                }
            }
        }
        DashedAddButton(strings.addRewardCta, Modifier.padding(top = 12.dp), onClick = onAdd)
    }
}

@Composable
private fun DoneStep() {
    val strings = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().padding(top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Paper.greenCircle),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(color = androidx.compose.ui.graphics.Color.White, size = 40.dp, stroke = 2.4f)
        }
        Text(strings.onbDoneTitle, Modifier.padding(top = 22.dp), style = handStyle(40.sp), textAlign = TextAlign.Center)
        Text(
            strings.onbDoneBody,
            Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
            style = sansStyle(15.sp, Paper.body, lineHeight = 23.sp),
            textAlign = TextAlign.Center,
        )
    }
}

/** Numbered clay circle used for objectives (26dp in onboarding). */
@Composable
fun NumberCircle(number: Int, size: androidx.compose.ui.unit.Dp = 26.dp, fontSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Paper.clay),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = sansStyle(fontSize, Paper.onDark, FontWeight.Bold))
    }
}
