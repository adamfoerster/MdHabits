package com.adamfoerster.mdhabits.di

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
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
    single<VaultFileSystem> { IosVaultFileSystem(get()) }
}
