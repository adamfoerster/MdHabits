package com.adamfoerster.mdhabits.platform

import android.content.Context
import com.adamfoerster.mdhabits.data.markdown.VaultWatcher
import com.adamfoerster.mdhabits.di.initKoinOnce
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/**
 * Entry into the shared code for Android components that run without [MainActivity] — the
 * home-screen widget and the AppFunction service a voice assistant calls. Their process may never
 * have opened the app, so this sets up the vault context and Koin, then re-reads the notes changed
 * on disk (the app's [VaultWatcher] only polls while it is open).
 */
internal object AndroidKoin {
    suspend fun ready(context: Context): Koin {
        if (AndroidVaultBridge.appContext == null) AndroidVaultBridge.appContext = context.applicationContext
        synchronized(this) { initKoinOnce() }
        val koin = KoinPlatform.getKoin()
        // A vault that can't be read right now keeps what is loaded, like the app's own watcher.
        try {
            koin.get<VaultWatcher>().refreshAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        return koin
    }
}
