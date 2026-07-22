package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPenaltyRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryThemeRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryValueRepository
import com.adamfoerster.mdhabits.domain.model.Penalty
import com.adamfoerster.mdhabits.domain.model.PersonalValue
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.ApplyPenaltyUseCase
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.ui.screens.home.HomeViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {

    private fun newViewModel(
        tasks: InMemoryTaskRepository = InMemoryTaskRepository(),
        ledger: InMemoryPointsLedgerRepository = InMemoryPointsLedgerRepository(),
        values: InMemoryValueRepository = InMemoryValueRepository(),
        penalties: InMemoryPenaltyRepository = InMemoryPenaltyRepository(),
    ): HomeViewModel {
        val theme = InMemoryThemeRepository()
        val wc = fixedWeekCalculator()
        return HomeViewModel(
            taskRepository = tasks,
            themeRepository = theme,
            ledgerRepository = ledger,
            penaltyRepository = penalties,
            valueRepository = values,
            completeTask = CompleteTaskUseCase(tasks, ledger),
            applyPenalty = ApplyPenaltyUseCase(ledger),
            weekCalculator = wc,
        )
    }

    @Test
    fun rowResolvesLinkedValueNames() = runTest {
        val tasks = InMemoryTaskRepository()
        val values = InMemoryValueRepository()
        values.upsert(PersonalValue("v1", "Moderation"))
        tasks.upsert(Task("t1", "Meditate", points = 5, linkedValueIds = listOf("v1")))

        val vm = newViewModel(tasks = tasks, values = values)
        keepHot(vm.state)

        val row = vm.state.value.allTasks.first { it.task.id == "t1" }
        assertEquals(listOf("Moderation"), row.linkedNames)
        assertFalse(row.completed)
    }

    @Test
    fun completingTaskCreditsBalanceAndMarksRowDone() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Meditate", points = 5))

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        vm.onToggleComplete(Task("t1", "Meditate", points = 5), completed = true)

        assertEquals(5, ledger.currentBalance())
        assertEquals(5, vm.state.value.balance)
        assertTrue(vm.state.value.allTasks.first { it.task.id == "t1" }.completed)
    }

    @Test
    fun applyingPenaltyDeductsBalance() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        tasks.upsert(Task("t1", "Meditate", points = 20))

        val vm = newViewModel(tasks = tasks, ledger = ledger)
        keepHot(vm.state)

        vm.onToggleComplete(Task("t1", "Meditate", points = 20), completed = true)
        vm.onApplyPenalty(Penalty("p1", "Skipped", pointCost = 8))

        assertEquals(12, vm.state.value.balance)
    }
}
