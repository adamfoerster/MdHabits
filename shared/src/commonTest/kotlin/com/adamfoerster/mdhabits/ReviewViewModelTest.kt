package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryWeeklyReviewRepository
import com.adamfoerster.mdhabits.domain.model.AnnualTheme
import com.adamfoerster.mdhabits.domain.model.Objective
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.review.ReviewViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewViewModelTest : MainDispatcherTest() {

    @Test
    fun reportSummarizesThePreviousWeek() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val wc = fixedWeekCalculator()
        val complete = CompleteTaskUseCase(tasks, ledger)
        tasks.upsert(Task("t1", "A", points = 30))
        // The review looks back: the completion happened in the week that just ended.
        complete(Task("t1", "A", points = 30), wc.previousWeekId(), nowCompleted = true)

        val vm = ReviewViewModel(
            tasks, ledger, InMemoryWeeklyReviewRepository(), InMemoryThemeRepository(), wc,
        )
        keepHot(vm.state)

        assertEquals(wc.previousWeekId(), vm.reviewWeekId)
        assertEquals(30, vm.state.value.report?.earned)
        assertEquals(1, vm.state.value.completedCount)
    }

    @Test
    fun commitmentsStartWithEveryTaskAndCanBeToggledOff() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "A", points = 10))
        tasks.upsert(Task("t2", "B", points = 5))
        val wc = fixedWeekCalculator()

        val vm = ReviewViewModel(
            tasks, InMemoryPointsLedgerRepository(), InMemoryWeeklyReviewRepository(),
            InMemoryThemeRepository(), wc,
        )
        keepHot(vm.state)

        assertEquals(setOf("t1", "t2"), vm.state.value.commitIds)
        vm.toggleCommit("t2")
        assertEquals(setOf("t1"), vm.state.value.commitIds)
    }

    @Test
    fun submitWritesReviewIntoPreviousWeekAndPlansTheNewWeek() = runTest {
        val tasks = InMemoryTaskRepository()
        tasks.upsert(Task("t1", "A", points = 10))
        tasks.upsert(Task("t2", "B", points = 5))
        val reviews = InMemoryWeeklyReviewRepository()
        val theme = InMemoryThemeRepository()
        theme.upsertTheme(AnnualTheme("th", 2026, "Year", objectives = listOf(Objective("o1", "Read", 80))))
        val wc = fixedWeekCalculator()

        val vm = ReviewViewModel(tasks, InMemoryPointsLedgerRepository(), reviews, theme, wc)
        keepHot(vm.state)

        vm.toggleCommit("t2") // drop t2 from the new week
        vm.setJournal("Solid week")
        vm.setProximity(5)

        var done = false
        vm.submit(onDone = { done = true })
        assertTrue(done)

        // The journal lands on the week being reviewed (the previous one)…
        assertEquals(wc.previousWeekId(), vm.reviewWeekId)
        val saved = reviews.getReview(vm.reviewWeekId)!!
        assertEquals("Solid week", saved.journal)
        assertEquals(setOf("t1"), saved.plannedTaskIds.toSet())
        assertEquals(mapOf("o1" to 5), saved.objectiveRatings)

        // …and the commitments start the current week.
        assertEquals(wc.weekId(), vm.planWeekId)
        val planned = tasks.observeInstances(vm.planWeekId).first()
            .filter { it.planned }
            .map { it.taskId }
            .toSet()
        assertEquals(setOf("t1"), planned)
        assertTrue(tasks.observeWeekStarted(vm.planWeekId).first())
    }
}
