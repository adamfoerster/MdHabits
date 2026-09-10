package com.adamfoerster.mdhabits.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.usecase.SyncMdPrayerUseCase
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultPicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Outcome of [SettingsViewModel.pickMdPrayerFolder], for the screen to react to (e.g. a toast). */
sealed interface MdPrayerFolderResult {
    data class Linked(val displayName: String) : MdPrayerFolderResult
    data object NotFound : MdPrayerFolderResult
    data object Cancelled : MdPrayerFolderResult
}

class SettingsViewModel(
    private val settings: AppSettings,
    private val vaultPicker: VaultPicker,
    private val vaultMigrator: VaultMigrator,
    themeRepository: ThemeRepository,
    weekCalculator: WeekCalculator,
    private val localeController: LocaleController,
    appInfo: AppInfo,
    private val mdPrayerRepository: MdPrayerRepository,
    taskRepository: TaskRepository,
    private val syncMdPrayerUseCase: SyncMdPrayerUseCase,
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

    // ---- mdPrayer integration ----

    private val _mdPrayerEnabled = MutableStateFlow(settings.mdPrayerEnabled)
    val mdPrayerEnabled: StateFlow<Boolean> = _mdPrayerEnabled.asStateFlow()

    private val _mdPrayerFolderName = MutableStateFlow(settings.mdPrayerFolderDisplayName)
    val mdPrayerFolderName: StateFlow<String?> = _mdPrayerFolderName.asStateFlow()

    private val _mdPrayerLinkedTaskId = MutableStateFlow(settings.mdPrayerLinkedTaskId)
    val mdPrayerLinkedTaskId: StateFlow<String?> = _mdPrayerLinkedTaskId.asStateFlow()

    /** Active tasks the user can link mdPrayer completions to. */
    val mdPrayerTasks: StateFlow<List<Task>> = taskRepository.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mdPrayerLinkedTaskTitle: StateFlow<String?> = combine(
        mdPrayerTasks,
        _mdPrayerLinkedTaskId,
    ) { tasks, taskId -> tasks.find { it.id == taskId }?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setMdPrayerEnabled(enabled: Boolean) {
        settings.mdPrayerEnabled = enabled
        _mdPrayerEnabled.value = enabled
    }

    /** Launches the folder picker and, if the folder looks like an mdPrayer vault, persists it. */
    fun pickMdPrayerFolder(onResult: (MdPrayerFolderResult) -> Unit) = viewModelScope.launch {
        val selection = vaultPicker.pickVault() ?: run { onResult(MdPrayerFolderResult.Cancelled); return@launch }
        if (mdPrayerRepository.looksLikeMdPrayerVault(selection.ref)) {
            settings.mdPrayerFolderRef = selection.ref
            settings.mdPrayerFolderDisplayName = selection.displayName
            _mdPrayerFolderName.value = selection.displayName
            onResult(MdPrayerFolderResult.Linked(selection.displayName))
        } else {
            onResult(MdPrayerFolderResult.NotFound)
        }
    }

    fun linkMdPrayerTask(task: Task) {
        settings.mdPrayerLinkedTaskId = task.id
        _mdPrayerLinkedTaskId.value = task.id
    }

    /** Runs the sync immediately instead of waiting for the next app open. */
    fun syncMdPrayerNow(onDone: () -> Unit) = viewModelScope.launch {
        syncMdPrayerUseCase()
        onDone()
    }
}
