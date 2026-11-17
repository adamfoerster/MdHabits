package com.adamfoerster.mdhabits.platform

import android.content.Context
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.data.markdown.VaultWatcher
import com.adamfoerster.mdhabits.di.initKoinOnce
import com.adamfoerster.mdhabits.domain.usecase.WidgetSnapshot
import com.adamfoerster.mdhabits.domain.usecase.WidgetTasksUseCase
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.CancellationException
import org.koin.core.Koin
import org.koin.mp.KoinPlatform

/** What the home-screen widget draws: the tasks and the strings of the app's language. */
class WidgetContent(val snapshot: WidgetSnapshot, val strings: Strings)

/**
 * The home-screen widget's way into the shared code. The widget can run in a process the app's
 * activity never started, so every call sets up the vault context and Koin first, then re-reads
 * the notes that changed on disk (the app's [VaultWatcher] only polls while it is open).
 */
object AndroidWidgetData {

    suspend fun load(context: Context): WidgetContent {
        val koin = koin(context)
        refresh(koin)
        val snapshot = try {
            koin.get<WidgetTasksUseCase>().snapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            WidgetSnapshot(emptyList(), emptyList())
        }
        return WidgetContent(snapshot, stringsFor(koin.get<LocaleController>().lang.value))
    }

    /** Checks [taskId] off (or undoes it) and writes the week note out before the widget's process may die. */
    suspend fun toggle(context: Context, taskId: String) {
        val koin = koin(context)
        refresh(koin)
        if (koin.get<WidgetTasksUseCase>().toggle(taskId)) koin.get<VaultFileSystem>().flush()
    }

    private fun koin(context: Context): Koin {
        if (AndroidVaultBridge.appContext == null) AndroidVaultBridge.appContext = context.applicationContext
        synchronized(this) { initKoinOnce() }
        return KoinPlatform.getKoin()
    }

    /** A vault that can't be read right now keeps what is loaded, like the app's own watcher. */
    private suspend fun refresh(koin: Koin) {
        try {
            koin.get<VaultWatcher>().refreshAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
