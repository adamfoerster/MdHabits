package com.adamfoerster.mdhabits.platform

import android.content.Context
import com.adamfoerster.mdhabits.domain.usecase.CreateTaskUseCase
import com.adamfoerster.mdhabits.storage.VaultFileSystem

/** A task [AndroidTaskCreator] saved, in plain types for the AppFunction's answer. */
class SavedTask(
    val id: String,
    val title: String,
    val points: Int,
    /** The [com.adamfoerster.mdhabits.domain.model.Recurrence] name. */
    val frequency: String,
    /** English weekday names (`MONDAY`…); empty unless the task is a days-of-week one. */
    val daysOfWeek: List<String>,
)

/** Backs the "create task" AppFunction (Gemini): adds the task and writes its note out right away. */
object AndroidTaskCreator {
    /** @throws com.adamfoerster.mdhabits.domain.usecase.InvalidTaskRequestException for an invalid request. */
    suspend fun create(
        context: Context,
        title: String,
        points: Int?,
        frequency: String?,
        daysOfWeek: List<String>?,
    ): SavedTask {
        val koin = AndroidKoin.ready(context)
        val task = koin.get<CreateTaskUseCase>()(title, points, frequency, daysOfWeek)
        // The service's process may be gone right after the call; the queued write must not be.
        koin.get<VaultFileSystem>().flush()
        return SavedTask(task.id, task.title, task.points, task.recurrence.name, task.daysOfWeek.map { it.name })
    }
}
