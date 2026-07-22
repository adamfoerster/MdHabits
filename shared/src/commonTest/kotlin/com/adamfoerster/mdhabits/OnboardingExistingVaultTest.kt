package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.data.markdown.MarkdownCodecs
import com.adamfoerster.mdhabits.data.markdown.MarkdownPenaltyRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownRewardRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.VaultInspector
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultSelection
import com.adamfoerster.mdhabits.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Onboarding recognizes a picked folder that already holds an annual theme as an existing vault. */
class OnboardingExistingVaultTest : MainDispatcherTest() {

    private val existingTheme =
        MarkdownCodecs.encodeTheme(AnnualTheme("theme-vault", 2026, "Vault version"))

    private fun viewModel(
        settings: AppSettings,
        vault: RoutedFakeVaultFileSystem,
        selection: VaultSelection? = VaultSelection("MyVault", "picked-vault"),
    ) = OnboardingViewModel(
        MarkdownValueRepository(vault),
        MarkdownThemeRepository(vault),
        MarkdownTaskRepository(vault, MarkdownWeekStore(vault)),
        MarkdownPenaltyRepository(vault),
        MarkdownRewardRepository(vault),
        settings,
        FixedVaultPicker(selection),
        VaultMigrator(vault, settings),
        VaultInspector(vault),
        fixedWeekCalculator(),
        LocaleController(settings),
    )

    @Test
    fun pickingAFolderWithAThemeNoteFlagsAnExistingVault() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        vault.root("picked-vault").files["theme/2026.md"] = existingTheme
        val viewModel = viewModel(settings, vault)

        viewModel.pickFolder()

        assertTrue(viewModel.draft.value.vaultHasTheme)
    }

    @Test
    fun pickingAnEmptyFolderKeepsTheFullOnboarding() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        val viewModel = viewModel(settings, vault)

        viewModel.pickFolder()

        assertFalse(viewModel.draft.value.vaultHasTheme)
    }

    @Test
    fun anUndecodableThemeNoteDoesNotCountAsAnExistingVault() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        vault.root("picked-vault").files["theme/2026.md"] = "not a theme note"
        val viewModel = viewModel(settings, vault)

        viewModel.pickFolder()

        assertFalse(viewModel.draft.value.vaultHasTheme)
    }

    @Test
    fun finishingWithAnExistingVaultKeepsItsNotesAndSeedsNothing() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        val picked = vault.root("picked-vault")
        picked.files["theme/2026.md"] = existingTheme
        val viewModel = viewModel(settings, vault)

        viewModel.pickFolder()
        // Anything typed before the vault was recognized must not be written over its notes.
        viewModel.setThemeName("A new theme that must be discarded")
        viewModel.addValue("Health", "")
        viewModel.addTask("Meditate", 5)
        viewModel.finish()

        assertTrue(settings.onboardingComplete)
        assertEquals("picked-vault", settings.vaultRef)
        assertEquals(existingTheme, picked.files["theme/2026.md"])
        assertEquals(listOf("theme/2026.md"), picked.files.keys.toList())
    }
}
