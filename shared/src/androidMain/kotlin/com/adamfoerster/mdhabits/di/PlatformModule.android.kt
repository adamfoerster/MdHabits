package com.adamfoerster.mdhabits.di

import com.adamfoerster.mdhabits.core.platform.AppInfo
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import com.adamfoerster.mdhabits.platform.AndroidAppInfo
import com.adamfoerster.mdhabits.platform.AndroidAppSettings
import com.adamfoerster.mdhabits.platform.AndroidHealthConnectSource
import com.adamfoerster.mdhabits.platform.AndroidVaultBridge
import com.adamfoerster.mdhabits.platform.AndroidVaultFileSystem
import com.adamfoerster.mdhabits.platform.AndroidVaultPicker
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import com.adamfoerster.mdhabits.storage.VaultPicker
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<AppSettings> {
        AndroidAppSettings(
            requireNotNull(AndroidVaultBridge.appContext) { "AndroidVaultBridge.appContext not set" },
        )
    }
    single<AppInfo> {
        AndroidAppInfo(
            requireNotNull(AndroidVaultBridge.appContext) { "AndroidVaultBridge.appContext not set" },
        )
    }
    single<VaultPicker> { AndroidVaultPicker() }
    single<HealthDataSource> {
        AndroidHealthConnectSource(
            requireNotNull(AndroidVaultBridge.appContext) { "AndroidVaultBridge.appContext not set" },
        )
    }
    single<VaultFileSystem> {
        AndroidVaultFileSystem(
            requireNotNull(AndroidVaultBridge.appContext) { "AndroidVaultBridge.appContext not set" },
            get(),
        )
    }
}
