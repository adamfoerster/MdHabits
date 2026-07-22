package com.adamfoerster.mdhabits.ui.screens.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.usecase.AchieveObjectiveUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(
    themeRepository: ThemeRepository,
    private val achieveObjective: AchieveObjectiveUseCase,
    private val weekCalculator: WeekCalculator,
) : ViewModel() {

    val year = weekCalculator.today().year

    val theme: StateFlow<AnnualTheme?> = themeRepository.observeTheme(year)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onToggleAchieved(objective: Objective) = viewModelScope.launch {
        achieveObjective(weekCalculator.weekId(), objective, !objective.achieved)
    }
}
