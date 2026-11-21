package com.adamfoerster.mdhabits.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.appfunctions.AppFunctionStringValueConstraint
import com.adamfoerster.mdhabits.domain.usecase.InvalidTaskRequestException
import com.adamfoerster.mdhabits.platform.AndroidTaskCreator
import com.adamfoerster.mdhabits.widget.TasksWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A task as saved in the user's MdHabits journal.
 *
 * @property id The task's unique id.
 * @property title The task's title.
 * @property points The points the user earns when completing it (or loses each day a HABIT is missed).
 * @property frequency How often the task is due: ADHOC, DAILY, WEEKLY, MONTHLY, DAYS_OF_WEEK or HABIT.
 * @property daysOfWeek The weekdays a DAYS_OF_WEEK task is due on, in English (MONDAY…SUNDAY); empty otherwise.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class CreatedTask(
    val id: String,
    val title: String,
    val points: Int,
    val frequency: String,
    val daysOfWeek: List<String>,
)

/**
 * AppFunctions of MdHabits, a habit journal where completing tasks earns points. The KSP compiler
 * generates the concrete `TaskAppFunctionService` declared in the manifest.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "TaskAppFunctionService",
    appFunctionXmlFileName = "task_app_function_service",
)
abstract class BaseTaskAppFunctionService : AppFunctionService() {

    /**
     * Adds a new task (a to-do or a habit) to the user's MdHabits journal. Use it when the user asks
     * to add, create or remember a task, habit or to-do in MdHabits.
     *
     * @param title What the task is, as the user said it, in the user's language, e.g. "Buy bread". Required; at most 200 characters.
     * @param points Points earned when the task is done, from 1 to 1000. Omit it unless the user says a number; it defaults to 5.
     * @param frequency How often the task is due. ADHOC: a one-off to-do, done once (the default). DAILY: every day. WEEKLY: once a week, any day. MONTHLY: once a month, any day. DAYS_OF_WEEK: on specific weekdays, given in daysOfWeek. HABIT: every day, and missing a day costs its points. Omit it for a one-off task.
     * @param daysOfWeek The weekdays a DAYS_OF_WEEK task is due on, in English: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY. Giving days without a frequency makes it a DAYS_OF_WEEK task.
     * @return The task as saved.
     * @throws AppFunctionInvalidArgumentException If the title is empty or too long, points are out of range, the frequency or a day is unknown, DAYS_OF_WEEK has no days, or a pending task with the same title already exists. If thrown, tell the user what was wrong and ask them to correct it.
     * @throws AppFunctionAppUnknownException If the task couldn't be saved. If thrown, suggest the user retry later or open MdHabits.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun createTask(
        title: String,
        points: Int? = null,
        @AppFunctionStringValueConstraint(enumValues = ["ADHOC", "DAILY", "WEEKLY", "MONTHLY", "DAYS_OF_WEEK", "HABIT"])
        frequency: String? = null,
        daysOfWeek: List<String>? = null,
    ): CreatedTask = withContext(Dispatchers.Default) {
        val task = try {
            AndroidTaskCreator.create(applicationContext, title, points, frequency, daysOfWeek)
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidTaskRequestException) {
            throw AppFunctionInvalidArgumentException(e.message)
        } catch (e: Exception) {
            throw AppFunctionAppUnknownException("Couldn't save the task: ${e.message}")
        }
        // A new per-day or weekly task belongs on the home-screen widget.
        runCatching { TasksWidget.refresh(applicationContext) }
        CreatedTask(
            id = task.id,
            title = task.title,
            points = task.points,
            frequency = task.frequency,
            daysOfWeek = task.daysOfWeek,
        )
    }
}
