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
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.storage.VaultPicker
import com.adamfoerster.mdhabits.storage.VaultSelection
import com.adamfoerster.mdhabits.ui.onboarding.OnboardingViewModel
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [VaultFileSystem] that, like the real platform implementations, resolves the storage root from
 * [AppSettings.vaultRef] on every call — data written before a vault is picked lands in a separate
 * fallback root, which is exactly the behavior the migration guards against.
 */
class RoutedFakeVaultFileSystem(private val settings: AppSettings) : VaultFileSystem {
    private val roots = mutableMapOf<String, FakeVaultFileSystem>()

    fun root(ref: String?): FakeVaultFileSystem = roots.getOrPut(ref ?: "fallback") { FakeVaultFileSystem() }
    private fun current() = root(settings.vaultRef)

    override suspend fun list(dir: String): List<String> = current().list(dir)
    override suspend fun read(dir: String, name: String): String? = current().read(dir, name)
    override suspend fun write(dir: String, name: String, content: String) = current().write(dir, name, content)
    override suspend fun delete(dir: String, name: String) = current().delete(dir, name)

    // Ref-based peeks resolve the given ref, like the platform implementations.
    override suspend fun listIn(ref: String, dir: String): List<String> = root(ref).list(dir)
    override suspend fun readIn(ref: String, dir: String, name: String): String? = root(ref).read(dir, name)
}

class FixedVaultPicker(private val selection: VaultSelection?) : VaultPicker {
    override suspend fun pickVault(): VaultSelection? = selection
}

class OnboardingVaultPersistenceTest : MainDispatcherTest() {

    @Test
    fun finishWritesOnboardingDataIntoThePickedVault() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        val viewModel = OnboardingViewModel(
            MarkdownValueRepository(vault),
            MarkdownThemeRepository(vault),
            MarkdownTaskRepository(vault, MarkdownWeekStore(vault)),
            MarkdownPenaltyRepository(vault),
            MarkdownRewardRepository(vault),
            settings,
            FixedVaultPicker(VaultSelection("MyVault", "picked-vault")),
            VaultMigrator(vault, settings),
            VaultInspector(vault),
            fixedWeekCalculator(),
            LocaleController(settings),
        )

        viewModel.pickFolder()
        viewModel.addValue("Health", "")
        viewModel.setThemeName("Year of Health")
        viewModel.addObjective("Run a marathon", 100)
        viewModel.addTask("Meditate", 5)
        viewModel.addPenalty("Junk food", 10)
        viewModel.finish()

        assertEquals("picked-vault", settings.vaultRef)
        // Simulate a relaunch: fresh repositories load from the picked vault.
        val theme = MarkdownThemeRepository(vault).getTheme(2026)
        assertEquals("Year of Health", theme?.name)
        assertEquals(listOf("Run a marathon"), theme?.objectives?.map { it.title })
        assertEquals(
            listOf("Meditate"),
            MarkdownTaskRepository(vault, MarkdownWeekStore(vault)).observeTasks().first().map { it.title },
        )
        assertEquals(
            listOf("Junk food"),
            MarkdownPenaltyRepository(vault).observePenalties().first().map { it.name },
        )
        assertEquals(listOf("Health"), MarkdownValueRepository(vault).observeValues().first().map { it.name })
        assertTrue(vault.root(null).files.isEmpty(), "nothing should be written to the fallback root")
    }
}

class SettingsVaultMigrationTest : MainDispatcherTest() {

    @Test
    fun pickingAFolderInSettingsCopiesExistingNotesIntoIt() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        // Notes created before a folder was picked live in the fallback root.
        MarkdownThemeRepository(vault).upsertTheme(AnnualTheme("theme-1", 2026, "Year of Health"))
        MarkdownTaskRepository(vault, MarkdownWeekStore(vault)).upsert(Task("t-1", "Meditate", 5))
        MarkdownPenaltyRepository(vault).upsert(Penalty("p-1", "Junk food", 10))
        val viewModel = SettingsViewModel(
            settings,
            FixedVaultPicker(VaultSelection("MyVault", "picked-vault")),
            VaultMigrator(vault, settings),
            InMemoryThemeRepository(),
            fixedWeekCalculator(),
            LocaleController(settings),
            FakeAppInfo(),
        )

        viewModel.pickFolder()

        assertEquals("picked-vault", settings.vaultRef)
        assertEquals("Year of Health", MarkdownThemeRepository(vault).getTheme(2026)?.name)
        assertEquals("Meditate", MarkdownTaskRepository(vault, MarkdownWeekStore(vault)).getTask("t-1")?.title)
        assertEquals("Junk food", MarkdownPenaltyRepository(vault).getPenalty("p-1")?.name)
    }

    @Test
    fun migrationNeverOverwritesNotesAlreadyInTheNewVault() = runTest {
        val settings = FakeAppSettings()
        val vault = RoutedFakeVaultFileSystem(settings)
        vault.root("picked-vault").files["theme/2026.md"] =
            MarkdownCodecs.encodeTheme(AnnualTheme("theme-vault", 2026, "Vault version"))
        MarkdownThemeRepository(vault).upsertTheme(AnnualTheme("theme-old", 2026, "Fallback version"))

        VaultMigrator(vault, settings).migrateTo(VaultSelection("MyVault", "picked-vault"))

        assertEquals("Vault version", MarkdownThemeRepository(vault).getTheme(2026)?.name)
    }
}
