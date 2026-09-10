package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.i18n.Lang
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.langFromTag
import com.adamfoerster.mdhabits.core.i18n.systemLanguageTag
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.markdown.VaultInspector
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import com.adamfoerster.mdhabits.ui.onboarding.OnboardingViewModel
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FakeAppSettings : AppSettings {
    override var onboardingComplete: Boolean = false
    override var vaultDisplayName: String? = null
    override var vaultRef: String? = null
    override var languageTag: String? = null
    override var mdPrayerEnabled: Boolean = false
    override var mdPrayerFolderDisplayName: String? = null
    override var mdPrayerFolderRef: String? = null
    override var mdPrayerLinkedTaskId: String? = null
}

class FakeVaultPicker : VaultPicker {
    override suspend fun pickVault(): VaultSelection? = null
}

class LangFromTagTest {

    @Test
    fun resolvesRegionalTagsToSupportedLanguages() {
        assertEquals(Lang.PT, langFromTag("pt-BR"))
        assertEquals(Lang.ES, langFromTag("es-MX"))
        assertEquals(Lang.EN, langFromTag("en-US"))
    }

    @Test
    fun unknownOrMissingTagsFallBackToEnglish() {
        assertEquals(Lang.EN, langFromTag("fr"))
        assertEquals(Lang.EN, langFromTag(null))
    }
}

class LocaleControllerTest {

    @Test
    fun startsFromTheSavedLanguage() {
        val settings = FakeAppSettings().apply { languageTag = "es" }
        assertEquals(Lang.ES, LocaleController(settings).lang.value)
    }

    @Test
    fun setLanguagePersistsAndUpdatesTheFlow() {
        val settings = FakeAppSettings()
        val controller = LocaleController(settings)

        controller.setLanguage("pt")

        assertEquals("pt", settings.languageTag)
        assertEquals(Lang.PT, controller.lang.value)
    }

    @Test
    fun nullFollowsTheSystemLanguage() {
        val controller = LocaleController(FakeAppSettings().apply { languageTag = "es" })

        controller.setLanguage(null)

        assertEquals(langFromTag(systemLanguageTag()), controller.lang.value)
    }
}

class LanguageSelectionViewModelTest : MainDispatcherTest() {

    @Test
    fun settingsViewModelAppliesAndPersistsTheLanguage() = runTest {
        val settings = FakeAppSettings()
        val controller = LocaleController(settings)
        val viewModel = SettingsViewModel(
            settings, FakeVaultPicker(), VaultMigrator(FakeVaultFileSystem(), settings),
            InMemoryThemeRepository(), fixedWeekCalculator(), controller, FakeAppInfo(),
            NoopMdPrayerRepository(), InMemoryTaskRepository(), disabledMdPrayerSync(),
        )

        viewModel.setLanguage(Lang.PT)

        assertEquals(Lang.PT, viewModel.lang.value)
        assertEquals("pt", settings.languageTag)
    }

    @Test
    fun settingsViewModelExposesTheInstalledAppVersion() = runTest {
        val settings = FakeAppSettings()
        val viewModel = SettingsViewModel(
            settings, FakeVaultPicker(), VaultMigrator(FakeVaultFileSystem(), settings),
            InMemoryThemeRepository(), fixedWeekCalculator(), LocaleController(settings),
            FakeAppInfo(version = "0.8.0"),
            NoopMdPrayerRepository(), InMemoryTaskRepository(), disabledMdPrayerSync(),
        )

        assertEquals("0.8.0", viewModel.version)
    }

    @Test
    fun onboardingViewModelAppliesAndPersistsTheLanguage() = runTest {
        val settings = FakeAppSettings()
        val controller = LocaleController(settings)
        val viewModel = OnboardingViewModel(
            InMemoryValueRepository(),
            InMemoryThemeRepository(),
            InMemoryTaskRepository(),
            InMemoryPenaltyRepository(),
            InMemoryRewardRepository(),
            settings,
            FakeVaultPicker(),
            VaultMigrator(FakeVaultFileSystem(), settings),
            VaultInspector(FakeVaultFileSystem()),
            fixedWeekCalculator(),
            controller,
        )

        viewModel.setLanguage(Lang.ES)

        assertEquals(Lang.ES, viewModel.lang.value)
        assertEquals("es", settings.languageTag)
    }
}
