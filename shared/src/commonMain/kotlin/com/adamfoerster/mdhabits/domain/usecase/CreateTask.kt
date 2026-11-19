package com.adamfoerster.mdhabits.domain.usecase

import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.core.newId
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.model.stampHabitSince
import com.adamfoerster.mdhabits.domain.repository.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DayOfWeek

/** A request [CreateTaskUseCase] can't carry out; [message] says what to fix, in English, for the caller. */
class InvalidTaskRequestException(message: String) : IllegalArgumentException(message)

/**
 * Adds a task asked for from outside the app — a voice assistant calling the Android AppFunction.
 * The input is loosely typed (the assistant sends text) and every field but [invoke]'s title is
 * optional, so this validates it and fills in the app's defaults: no frequency means ad-hoc, like a
 * new task on *Cadastros*, unless days of the week are given, which make it a days-of-week task.
 */
class CreateTaskUseCase(
    private val tasks: TaskRepository,
    private val weekCalculator: WeekCalculator,
) {
    /**
     * @param points null or 0 (what an assistant sends for an omitted number) means [DEFAULT_POINTS].
     * @param frequency a [Recurrence] name, any case; null or blank for the default.
     * @param daysOfWeek English weekday names (`MONDAY`…), any case; required for `DAYS_OF_WEEK`.
     * @throws InvalidTaskRequestException when a field is invalid, or a pending task with the same
     *   title already exists (an assistant retrying a request would otherwise add it twice).
     */
    suspend operator fun invoke(
        title: String,
        points: Int? = null,
        frequency: String? = null,
        daysOfWeek: List<String>? = null,
    ): Task {
        val name = title.trim()
        if (name.isEmpty()) throw InvalidTaskRequestException("The task title can't be empty.")
        if (name.length > MAX_TITLE_LENGTH) {
            throw InvalidTaskRequestException("The task title can't be longer than $MAX_TITLE_LENGTH characters.")
        }
        val value = points?.takeIf { it != 0 } ?: DEFAULT_POINTS
        if (value !in 1..MAX_POINTS) throw InvalidTaskRequestException("Points must be between 1 and $MAX_POINTS.")
        val days = parseDays(daysOfWeek.orEmpty())
        val recurrence = parseRecurrence(frequency) ?: if (days.isEmpty()) Recurrence.ADHOC else Recurrence.DAYS_OF_WEEK
        if (recurrence == Recurrence.DAYS_OF_WEEK && days.isEmpty()) {
            throw InvalidTaskRequestException("A DAYS_OF_WEEK task needs at least one day of the week.")
        }
        val folded = name.lowercase()
        val duplicate = tasks.observeTasks(activeOnly = true).first().any { it.doneOn == null && it.title.trim().lowercase() == folded }
        if (duplicate) throw InvalidTaskRequestException("A task named \"$name\" already exists.")

        val task = Task(
            id = newId("t"),
            title = name,
            points = value,
            recurrence = recurrence,
            daysOfWeek = if (recurrence == Recurrence.DAYS_OF_WEEK) days else emptyList(),
        ).stampHabitSince(weekCalculator.today())
        tasks.upsert(task)
        return task
    }

    private fun parseRecurrence(text: String?): Recurrence? {
        val key = text?.trim()?.uppercase()?.replace(' ', '_')?.replace('-', '_')
        if (key.isNullOrEmpty()) return null
        return Recurrence.entries.find { it.name == key }
            ?: throw InvalidTaskRequestException(
                "Unknown frequency \"$text\". Use one of: ${Recurrence.entries.joinToString { it.name }}.",
            )
    }

    private fun parseDays(texts: List<String>): List<DayOfWeek> = texts
        .filter { it.isNotBlank() }
        .map { text ->
            DayOfWeek.entries.find { it.name == text.trim().uppercase() }
                ?: throw InvalidTaskRequestException(
                    "Unknown day of the week \"$text\". Use English names: ${DayOfWeek.entries.joinToString { it.name }}.",
                )
        }
        .distinct()
        .sorted()

    companion object {
        const val DEFAULT_POINTS = 5
        const val MAX_POINTS = 1000
        const val MAX_TITLE_LENGTH = 200
    }
}
