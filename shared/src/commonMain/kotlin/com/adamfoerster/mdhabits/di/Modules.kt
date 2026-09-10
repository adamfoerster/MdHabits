package com.adamfoerster.mdhabits.di

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.data.markdown.MarkdownMdPrayerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownPenaltyRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownPointsLedgerRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownRewardRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownTaskRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownThemeRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownValueRepository
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeekStore
import com.adamfoerster.mdhabits.data.markdown.MarkdownWeeklyReviewRepository
import com.adamfoerster.mdhabits.data.markdown.VaultInspector
import com.adamfoerster.mdhabits.domain.repository.MdPrayerRepository
import com.adamfoerster.mdhabits.domain.repository.PenaltyRepository
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import com.adamfoerster.mdhabits.domain.repository.ThemeRepository
import com.adamfoerster.mdhabits.domain.repository.ValueRepository
import com.adamfoerster.mdhabits.domain.repository.WeeklyReviewRepository
import com.adamfoerster.mdhabits.domain.usecase.AchieveObjectiveUseCase
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.RedeemRewardUseCase
import com.adamfoerster.mdhabits.domain.usecase.SyncMdPrayerUseCase
import com.adamfoerster.mdhabits.storage.VaultMigrator
import com.adamfoerster.mdhabits.ui.onboarding.OnboardingViewModel
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import com.adamfoerster.mdhabits.ui.screens.redeem.RedeemViewModel
import com.adamfoerster.mdhabits.ui.screens.register.RegisterViewModel
import com.adamfoerster.mdhabits.ui.screens.review.ReviewViewModel
import com.adamfoerster.mdhabits.ui.screens.settings.SettingsViewModel
import com.adamfoerster.mdhabits.ui.screens.theme.ThemeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.bind
import org.koin.dsl.module

/** Platform-provided bindings: [com.adamfoerster.mdhabits.core.settings.AppSettings],
 *  [com.adamfoerster.mdhabits.storage.VaultPicker], and
 *  [com.adamfoerster.mdhabits.storage.VaultFileSystem]. */
expect fun platformModule(): Module

val dataModule = module {
    // One shared store: task instances, reviews, and the ledger live in the same week notes.
    single { MarkdownWeekStore(get()) }
    single { MarkdownValueRepository(get()) } bind ValueRepository::class
    single { MarkdownPenaltyRepository(get()) } bind PenaltyRepository::class
    single { MarkdownTaskRepository(get(), get()) } bind TaskRepository::class
    single { MarkdownRewardRepository(get()) } bind RewardRepository::class
    single { MarkdownThemeRepository(get()) } bind ThemeRepository::class
    single { MarkdownPointsLedgerRepository(get()) } bind PointsLedgerRepository::class
    single { MarkdownWeeklyReviewRepository(get()) } bind WeeklyReviewRepository::class
    single { MarkdownMdPrayerRepository(get()) } bind MdPrayerRepository::class
    single { VaultMigrator(get(), get()) }
    single { VaultInspector(get()) }
    single { LocaleController(get()) }
}

val domainModule = module {
    single { WeekCalculator() }
    factory { CompleteTaskUseCase(get(), get()) }
    factory { AchieveObjectiveUseCase(get(), get()) }
    factory { ApplyPenaltyUseCase(get()) }
    factory { RedeemRewardUseCase(get(), get()) }
    factory { SyncMdPrayerUseCase(get(), get(), get(), get(), get()) }
}

val viewModelModule = module {
    viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RedeemViewModel(get(), get(), get(), get()) }
    viewModel { RegisterViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ThemeViewModel(get(), get(), get()) }
    viewModel { ReviewViewModel(get(), get(), get(), get(), get()) }
}

/** Starts Koin with all modules. Call once from each platform entry point after platform setup. */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(platformModule(), dataModule, domainModule, viewModelModule)
}

private var koinStarted = false

/** Idempotent [initKoin]; safe to call from entry points that may run more than once. */
fun initKoinOnce() {
    if (!koinStarted) {
        koinStarted = true
        initKoin()
    }
}
