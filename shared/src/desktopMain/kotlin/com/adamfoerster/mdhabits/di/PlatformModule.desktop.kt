package com.adamfoerster.mdhabits.di

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import com.adamfoerster.mdhabits.domain.repository.UnsupportedHealthDataSource
import com.adamfoerster.mdhabits.platform.DesktopAppInfo
import com.adamfoerster.mdhabits.platform.DesktopAppSettings
import com.adamfoerster.mdhabits.platform.DesktopVaultFileSystem
import com.adamfoerster.mdhabits.platform.DesktopVaultPicker
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import com.adamfoerster.mdhabits.storage.VaultPicker
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<AppSettings> { DesktopAppSettings() }
    single<AppInfo> { DesktopAppInfo() }
    single<VaultPicker> { DesktopVaultPicker() }
    single<HealthDataSource> { UnsupportedHealthDataSource }
    single<VaultFileSystem> { DesktopVaultFileSystem(get()) }
}
