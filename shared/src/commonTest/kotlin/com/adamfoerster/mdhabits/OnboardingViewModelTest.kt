package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.markdown.VaultInspector
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.storage.VaultMigrator
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingViewModelTest : MainDispatcherTest() {

    @Test
    fun finishPersistsMultilineThemeDescription() = runTest {
        val themes = InMemoryThemeRepository()
        val settings = FakeAppSettings()
        val fileSystem = FakeVaultFileSystem()
        val viewModel = com.adamfoerster.mdhabits.ui.onboarding.OnboardingViewModel(
            InMemoryValueRepository(),
            themes,
            InMemoryTaskRepository(),
            InMemoryPenaltyRepository(),
            InMemoryRewardRepository(),
            settings,
            FakeVaultPicker(),
            VaultMigrator(fileSystem, settings),
            VaultInspector(fileSystem),
            fixedWeekCalculator(),
            LocaleController(settings),
        )
        val description = "Um ano de saúde.\nCorpo são,\nmente sã."

        viewModel.setThemeName("Ano da Saúde")
        viewModel.setThemeDescription(description)
        viewModel.finish()

        assertEquals(description, themes.getTheme(2026)?.description)
    }
}
