package com.adamfoerster.mdhabits.platform

import android.content.Context
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.Strings
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.domain.usecase.WidgetSnapshot
import com.adamfoerster.mdhabits.domain.usecase.WidgetTasksUseCase
import com.adamfoerster.mdhabits.storage.VaultFileSystem
import kotlinx.coroutines.CancellationException

/** What the home-screen widget draws: the tasks and the strings of the app's language. */
class WidgetContent(val snapshot: WidgetSnapshot, val strings: Strings)

/**
 * The home-screen widget's way into the shared code. Every call goes through [AndroidKoin.ready], since
 * the widget can run in a process the app's activity never started.
 */
object AndroidWidgetData {

    suspend fun load(context: Context): WidgetContent {
        val koin = AndroidKoin.ready(context)
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
        val koin = AndroidKoin.ready(context)
        if (koin.get<WidgetTasksUseCase>().toggle(taskId)) koin.get<VaultFileSystem>().flush()
    }
}
