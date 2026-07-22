package com.adamfoerster.mdhabits.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultPicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    settings: AppSettings,
    private val vaultPicker: VaultPicker,
    private val vaultMigrator: VaultMigrator,
    themeRepository: ThemeRepository,
    weekCalculator: WeekCalculator,
    private val localeController: LocaleController,
    appInfo: AppInfo,
) : ViewModel() {

    /** The installed app version shown in the About section. */
    val version: String = appInfo.version

    private val _vaultName = MutableStateFlow(settings.vaultDisplayName)
    val vaultName: StateFlow<String?> = _vaultName.asStateFlow()

    /** The effective UI language; changes apply app-wide immediately and are persisted. */
    val lang = localeController.lang

    fun setLanguage(lang: Lang) = localeController.setLanguage(lang.tag)

    val themeName: StateFlow<String> = themeRepository.observeTheme(weekCalculator.today().year)
        .map { it?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun pickFolder() = viewModelScope.launch {
        val selection = vaultPicker.pickVault() ?: return@launch
        // Copies the existing notes into the new folder besides persisting the selection.
        vaultMigrator.migrateTo(selection)
        _vaultName.value = selection.displayName
    }
}
