package com.adamfoerster.mdhabits

import com.adamfoerster.mdhabits.data.repo.InMemoryPointsLedgerRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryRewardRepository
import com.adamfoerster.mdhabits.data.repo.InMemoryTaskRepository
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.model.Task
import com.adamfoerster.mdhabits.domain.usecase.CompleteTaskUseCase
import com.adamfoerster.mdhabits.domain.usecase.RedeemResult
import com.adamfoerster.mdhabits.domain.usecase.RedeemRewardUseCase
import com.adamfoerster.mdhabits.ui.screens.redeem.RedeemViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RedeemViewModelTest : MainDispatcherTest() {

    @Test
    fun redeemingAffordableRewardDeductsBalance() = runTest {
        val rewards = InMemoryRewardRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val tasks = InMemoryTaskRepository()
        val complete = CompleteTaskUseCase(tasks, ledger)
        val wc = fixedWeekCalculator()
        rewards.upsert(Reward("r1", "Cinema", pointCost = 50))
        complete(Task("t1", "A", points = 60), wc.weekId(), nowCompleted = true)

        val vm = RedeemViewModel(rewards, ledger, RedeemRewardUseCase(rewards, ledger), wc)
        keepHot(vm.state)

        var result: RedeemResult? = null
        vm.onRedeem(rewards.getReward("r1")!!) { result = it }

        assertEquals(RedeemResult.Success, result)
        assertEquals(10, vm.state.value.balance)
    }

    @Test
    fun redeemingUnaffordableRewardIsBlocked() = runTest {
        val rewards = InMemoryRewardRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val wc = fixedWeekCalculator()
        rewards.upsert(Reward("r1", "Trip", pointCost = 500))

        val vm = RedeemViewModel(rewards, ledger, RedeemRewardUseCase(rewards, ledger), wc)
        keepHot(vm.state)

        var result: RedeemResult? = null
        vm.onRedeem(rewards.getReward("r1")!!) { result = it }

        assertEquals(RedeemResult.InsufficientBalance(balance = 0, cost = 500), result)
        assertEquals(0, vm.state.value.balance)
    }

    @Test
    fun stateExposesRewardsAndBalance() = runTest {
        val rewards = InMemoryRewardRepository()
        val ledger = InMemoryPointsLedgerRepository()
        val wc = fixedWeekCalculator()
        rewards.upsert(Reward("r1", "Cinema", pointCost = 50))
        rewards.upsert(Reward("r2", "Dinner", pointCost = 120))

        val vm = RedeemViewModel(rewards, ledger, RedeemRewardUseCase(rewards, ledger), wc)
        keepHot(vm.state)

        assertEquals(2, vm.state.value.rewards.size)
        assertEquals(0, vm.state.value.balance)
    }
}
