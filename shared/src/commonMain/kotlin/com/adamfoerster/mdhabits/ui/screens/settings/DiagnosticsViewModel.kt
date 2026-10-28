package com.adamfoerster.mdhabits.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.data.markdown.PointsReport
import kotlinx.coroutines.launch

/** Backs the Settings "points report" row: writes the diagnostic note into the vault. */
class DiagnosticsViewModel(private val pointsReport: PointsReport) : ViewModel() {

    /** Writes the report and hands back its path in the vault, or null if writing failed. */
    fun writePointsReport(onDone: (String?) -> Unit) = viewModelScope.launch {
        onDone(runCatching { pointsReport.write() }.getOrNull())
    }
}
