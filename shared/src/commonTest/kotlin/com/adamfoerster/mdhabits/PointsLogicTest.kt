package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.RedeemResult
import com.adamfoerster.mdhabits.domain.usecase.RedeemRewardUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PointsLogicTest {

    @Test
    fun completingTaskCreditsPoints() = runTest {
        val tasks = InMemoryTaskRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val complete = CompleteTaskUseCase(tasks, ledger)
        val task = Task(id = "t1", title = "Correr", points = 20)

        complete(task, "2026-W27", nowCompleted = true)
        assertEquals(20, ledger.currentBalance())

        // Undoing reverses the credit.
        complete(task, "2026-W27", nowCompleted = false)
        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun redeemBlockedWhenBalanceTooLow() = runTest {
        val rewards = InMemoryRewardRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val redeem = RedeemRewardUseCase(rewards, ledger)
        val reward = Reward(id = "r1", name = "Cinema", pointCost = 100)
        rewards.upsert(reward)

        val blocked = redeem("2026-W27", reward)
        assertTrue(blocked is RedeemResult.InsufficientBalance)
        assertEquals(0, ledger.currentBalance())
    }

    @Test
    fun redeemSucceedsAndDeductsWhenAffordable() = runTest {
        val tasks = InMemoryTaskRepository()
        val rewards = InMemoryRewardRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val complete = CompleteTaskUseCase(tasks, ledger)
        val redeem = RedeemRewardUseCase(rewards, ledger)
        val reward = Reward(id = "r1", name = "Cinema", pointCost = 100)
        rewards.upsert(reward)

        // Earn 120 points.
        complete(Task(id = "t1", title = "A", points = 60), "2026-W27", nowCompleted = true)
        complete(Task(id = "t2", title = "B", points = 60), "2026-W27", nowCompleted = true)

        val result = redeem("2026-W27", reward)
        assertEquals(RedeemResult.Success, result)
        assertEquals(20, ledger.currentBalance())
    }
}
