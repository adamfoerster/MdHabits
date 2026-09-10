package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.datetime.WeekRange
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncMdPrayerUseCase
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultSelection
import com.adamfoerster.mdhabits.ui.screens.settings.MdPrayerFolderResult
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** An [MdPrayerRepository] whose detection result is fixed for the test. */
private class StubMdPrayerRepository(private val looksValid: Boolean) : MdPrayerRepository {
    override suspend fun looksLikeMdPrayerVault(ref: String): Boolean = looksValid
    override suspend fun completedDates(ref: String, range: WeekRange): Set<LocalDate> = emptySet()
}

class SettingsMdPrayerTest : MainDispatcherTest() {

    private fun newViewModel(
        settings: FakeAppSettings = FakeAppSettings(),
        mdPrayerRepository: MdPrayerRepository = StubMdPrayerRepository(true),
        tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        vaultPicker: FixedVaultPicker = FixedVaultPicker(VaultSelection("Prayer", "prayer-ref")),
    ): SettingsViewModel {
        val ledger = InMemoryPointsLedgerRepository()
        return SettingsViewModel(
            settings, vaultPicker, VaultMigrator(FakeVaultFileSystem(), settings),
            InMemoryThemeRepository(), fixedWeekCalculator(), LocaleController(settings), FakeAppInfo(),
            mdPrayerRepository, tasks,
            SyncMdPrayerUseCase(settings, mdPrayerRepository, tasks, CompleteTaskUseCase(tasks, ledger), fixedWeekCalculator()),
        )
    }

    @Test
    fun pickingAFolderThatLooksLikeMdPrayerPersistsIt() = runTest {
        val settings = FakeAppSettings()
        val viewModel = newViewModel(settings = settings, mdPrayerRepository = StubMdPrayerRepository(true))
        var result: MdPrayerFolderResult? = null

        viewModel.pickMdPrayerFolder { result = it }

        assertEquals("prayer-ref", settings.mdPrayerFolderRef)
        assertEquals("Prayer", settings.mdPrayerFolderDisplayName)
        assertEquals("Prayer", viewModel.mdPrayerFolderName.value)
        assertEquals(MdPrayerFolderResult.Linked("Prayer"), result)
    }

    @Test
    fun pickingAFolderWithoutMdPrayerDataIsNotPersisted() = runTest {
        val settings = FakeAppSettings()
        val viewModel = newViewModel(settings = settings, mdPrayerRepository = StubMdPrayerRepository(false))
        var result: MdPrayerFolderResult? = null

        viewModel.pickMdPrayerFolder { result = it }

        assertNull(settings.mdPrayerFolderRef)
        assertNull(viewModel.mdPrayerFolderName.value)
        assertEquals(MdPrayerFolderResult.NotFound, result)
    }

    @Test
    fun cancellingThePickerReportsCancelledAndChangesNothing() = runTest {
        val settings = FakeAppSettings()
        val viewModel = newViewModel(settings = settings, vaultPicker = FixedVaultPicker(null))
        var result: MdPrayerFolderResult? = null

        viewModel.pickMdPrayerFolder { result = it }

        assertNull(settings.mdPrayerFolderRef)
        assertEquals(MdPrayerFolderResult.Cancelled, result)
    }

    @Test
    fun linkingATaskPersistsItsIdAndExposesItsTitle() = runTest {
        val settings = FakeAppSettings()
        val tasks = InMemoryTaskRepository()
        val task = Task("t1", "Oração matinal", points = 5)
        tasks.upsert(task)
        val viewModel = newViewModel(settings = settings, tasks = tasks)
        keepHot(viewModel.mdPrayerLinkedTaskTitle)

        viewModel.linkMdPrayerTask(task)

        assertEquals("t1", settings.mdPrayerLinkedTaskId)
        assertEquals("Oração matinal", viewModel.mdPrayerLinkedTaskTitle.value)
    }

    @Test
    fun togglingTheIntegrationOffKeepsTheSavedFolderAndTask() = runTest {
        val settings = FakeAppSettings().apply {
            mdPrayerFolderRef = "prayer-ref"
            mdPrayerFolderDisplayName = "Prayer"
            mdPrayerLinkedTaskId = "t1"
        }
        val viewModel = newViewModel(settings = settings)

        viewModel.setMdPrayerEnabled(true)
        viewModel.setMdPrayerEnabled(false)

        assertFalse(settings.mdPrayerEnabled)
        assertEquals("prayer-ref", settings.mdPrayerFolderRef)
        assertEquals("t1", settings.mdPrayerLinkedTaskId)
    }
}
