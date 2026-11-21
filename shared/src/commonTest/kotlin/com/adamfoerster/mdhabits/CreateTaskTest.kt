package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.Recurrence
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.CreateTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.InvalidTaskRequestException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CreateTaskTest {

    private val repo = InMemoryTaskRepository()
    private val createTask = CreateTaskUseCase(repo, fixedWeekCalculator())

    @Test
    fun onlyATitleMakesAnAdhocTaskWithTheDefaultPoints() = runTest {
        val task = createTask("  Comprar pão  ")
        assertEquals("Comprar pão", task.title)
        assertEquals(Recurrence.ADHOC, task.recurrence)
        assertEquals(CreateTaskUseCase.DEFAULT_POINTS, task.points)
        assertEquals(listOf(task), repo.observeTasks().first())
    }

    @Test
    fun zeroPointsMeansTheDefault() = runTest {
        // An assistant sends 0 for an omitted number.
        assertEquals(CreateTaskUseCase.DEFAULT_POINTS, createTask("Ler", points = 0).points)
    }

    @Test
    fun frequencyIsCaseInsensitive() = runTest {
        assertEquals(Recurrence.DAILY, createTask("Meditar", points = 3, frequency = "daily").recurrence)
        assertEquals(Recurrence.WEEKLY, createTask("Faxina", frequency = " Weekly ").recurrence)
    }

    @Test
    fun aHabitIsStampedWithToday() = runTest {
        val task = createTask("Sem açúcar", frequency = "HABIT")
        assertEquals(LocalDate(2026, 7, 2), task.habitSince)
    }

    @Test
    fun daysAloneMakeADaysOfWeekTask() = runTest {
        val task = createTask("Academia", daysOfWeek = listOf("friday", "MONDAY", "monday"))
        assertEquals(Recurrence.DAYS_OF_WEEK, task.recurrence)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), task.daysOfWeek)
        assertNull(task.habitSince)
    }

    @Test
    fun daysAreDroppedForOtherFrequencies() = runTest {
        assertEquals(emptyList(), createTask("Correr", frequency = "DAILY", daysOfWeek = listOf("MONDAY")).daysOfWeek)
    }

    @Test
    fun rejectsInvalidRequests() = runTest {
        assertFailsWith<InvalidTaskRequestException> { createTask("   ") }
        assertFailsWith<InvalidTaskRequestException> { createTask("x".repeat(201)) }
        assertFailsWith<InvalidTaskRequestException> { createTask("Ler", points = -2) }
        assertFailsWith<InvalidTaskRequestException> { createTask("Ler", points = 1001) }
        assertFailsWith<InvalidTaskRequestException> { createTask("Ler", frequency = "FORTNIGHTLY") }
        assertFailsWith<InvalidTaskRequestException> { createTask("Ler", frequency = "DAYS_OF_WEEK") }
        assertFailsWith<InvalidTaskRequestException> { createTask("Ler", daysOfWeek = listOf("segunda")) }
        assertEquals(emptyList(), repo.observeTasks().first())
    }

    @Test
    fun rejectsADuplicateOfAPendingTask() = runTest {
        createTask("Ligar pro dentista")
        assertFailsWith<InvalidTaskRequestException> { createTask("ligar pro DENTISTA ") }
    }

    @Test
    fun allowsTheTitleOfAFinishedAdhocTask() = runTest {
        repo.upsert(Task("t-old", "Comprar pão", 2, Recurrence.ADHOC, doneOn = LocalDate(2026, 6, 1)))
        createTask("Comprar pão")
        assertEquals(2, repo.observeTasks().first().size)
    }
}
