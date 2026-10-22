package com.adamfoerster.mdhabits.di

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import com.adamfoerster.mdhabits.domain.repository.UnsupportedHealthDataSource
import com.adamfoerster.mdhabits.platform.IosReminderScheduler
import com.adamfoerster.mdhabits.platform.IosAppInfo
import com.adamfoerster.mdhabits.platform.IosAppSettings
import com.adamfoerster.mdhabits.platform.IosVaultFileSystem
import com.adamfoerster.mdhabits.platform.IosVaultPicker
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import com.adamfoerster.mdhabits.storage.VaultPicker
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<AppSettings> { IosAppSettings() }
    single<AppInfo> { IosAppInfo() }
    single<VaultPicker> { IosVaultPicker() }
    single<HealthDataSource> { UnsupportedHealthDataSource }
    single<ReminderScheduler> { IosReminderScheduler() }
    single<VaultFileSystem> { IosVaultFileSystem(get()) }
}
