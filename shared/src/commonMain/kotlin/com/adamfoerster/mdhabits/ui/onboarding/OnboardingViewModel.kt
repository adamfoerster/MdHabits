package com.adamfoerster.mdhabits.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.PenaltyRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.data.markdown.VaultInspector
import com.adamfoerster.mdhabits.domain.repository.ValueRepository
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek

/** Draft accumulated across the onboarding steps before it is persisted on finish. */
data class OnboardingDraft(
    val vault: VaultSelection? = null,
    /** The picked folder already holds an annual theme note, so the setup steps are skipped. */
    val vaultHasTheme: Boolean = false,
    val values: List<PersonalValue> = emptyList(),
    val themeName: String = "",
    val themeDescription: String = "",
    val objectives: List<Objective> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val penalties: List<Penalty> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val pickingFolder: Boolean = false,
    val finished: Boolean = false,
)

class OnboardingViewModel(
    private val valueRepository: ValueRepository,
    private val themeRepository: ThemeRepository,
    private val taskRepository: TaskRepository,
    private val penaltyRepository: PenaltyRepository,
    private val rewardRepository: RewardRepository,
    private val settings: AppSettings,
    private val vaultPicker: VaultPicker,
    private val vaultMigrator: VaultMigrator,
    private val vaultInspector: VaultInspector,
    private val weekCalculator: WeekCalculator,
    private val localeController: LocaleController,
) : ViewModel() {

    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft = _draft.asStateFlow()

    /** The effective UI language; the welcome step's selector applies changes app-wide. */
    val lang = localeController.lang

    fun setLanguage(lang: Lang) = localeController.setLanguage(lang.tag)

    fun pickFolder() {
        if (_draft.value.pickingFolder) return
        _draft.update { it.copy(pickingFolder = true) }
        viewModelScope.launch {
            val selection = vaultPicker.pickVault()
            if (selection == null) {
                _draft.update { it.copy(pickingFolder = false) }
            } else {
                val hasTheme = vaultInspector.hasAnnualTheme(selection)
                _draft.update {
                    it.copy(vault = selection, vaultHasTheme = hasTheme, pickingFolder = false)
                }
            }
        }
    }

    fun addValue(name: String, description: String) = _draft.update {
        it.copy(values = it.values + PersonalValue(newId("v"), name.trim(), description.trim()))
    }

    fun removeValue(id: String) = _draft.update { it.copy(values = it.values.filterNot { v -> v.id == id }) }

    fun setThemeName(name: String) = _draft.update { it.copy(themeName = name) }
    fun setThemeDescription(text: String) = _draft.update { it.copy(themeDescription = text) }

    fun addObjective(title: String, points: Int) = _draft.update {
        it.copy(objectives = it.objectives + Objective(newId("obj"), title.trim(), points))
    }

    fun removeObjective(id: String) =
        _draft.update { it.copy(objectives = it.objectives.filterNot { o -> o.id == id }) }

    fun addTask(
        title: String,
        points: Int,
        recurrence: Recurrence = Recurrence.WEEKLY,
        daysOfWeek: List<DayOfWeek> = emptyList(),
        valueIds: List<String> = emptyList(),
        objectiveIds: List<String> = emptyList(),
    ) = _draft.update {
        it.copy(
            tasks = it.tasks + Task(
                id = newId("t"),
                title = title.trim(),
                points = points,
                recurrence = recurrence,
                daysOfWeek = daysOfWeek,
                linkedValueIds = valueIds,
                linkedObjectiveIds = objectiveIds,
            ),
        )
    }

    fun removeTask(id: String) = _draft.update { it.copy(tasks = it.tasks.filterNot { t -> t.id == id }) }

    fun addPenalty(name: String, cost: Int) = _draft.update {
        it.copy(penalties = it.penalties + Penalty(newId("p"), name.trim(), cost))
    }

    fun removePenalty(id: String) =
        _draft.update { it.copy(penalties = it.penalties.filterNot { p -> p.id == id }) }

    fun addReward(name: String, cost: Int) = _draft.update {
        it.copy(rewards = it.rewards + Reward(newId("r"), name.trim(), cost))
    }

    fun removeReward(id: String) =
        _draft.update { it.copy(rewards = it.rewards.filterNot { r -> r.id == id }) }

    fun finish() {
        val d = _draft.value
        viewModelScope.launch {
            // The vault choice must be persisted before anything is written: repositories write
            // through VaultFileSystem, which resolves the folder from settings on every call.
            d.vault?.let { vaultMigrator.migrateTo(it) }

            // An existing vault (it already holds an annual theme) is the source of truth: the
            // setup steps were skipped, so nothing may be seeded over its notes.
            if (!d.vaultHasTheme) {
                d.values.forEach { valueRepository.upsert(it) }
                themeRepository.upsertTheme(
                    AnnualTheme(
                        id = newId("theme"),
                        year = weekCalculator.today().year,
                        name = d.themeName.trim(),
                        description = d.themeDescription.trim(),
                        objectives = d.objectives,
                    ),
                )
                d.tasks.forEach { taskRepository.upsert(it) }
                d.penalties.forEach { penaltyRepository.upsert(it) }
                d.rewards.forEach { rewardRepository.upsert(it) }

                // Plan the created weekly tasks for the current week so Home shows them immediately.
                taskRepository.setPlanned(weekCalculator.weekId(), d.tasks.map { it.id })
            }

            settings.onboardingComplete = true
            _draft.update { it.copy(finished = true) }
        }
    }
}
