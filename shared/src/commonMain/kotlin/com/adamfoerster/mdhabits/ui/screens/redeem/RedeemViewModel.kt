package com.adamfoerster.mdhabits.ui.screens.redeem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamfoerster.mdhabits.core.datetime.WeekCalculator
import com.adamfoerster.mdhabits.domain.model.Reward
import com.adamfoerster.mdhabits.domain.repository.PointsLedgerRepository
import com.adamfoerster.mdhabits.domain.repository.RewardRepository
import com.adamfoerster.mdhabits.domain.usecase.RedeemResult
import com.adamfoerster.mdhabits.domain.usecase.RedeemRewardUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RedeemUiState(
    val balance: Int = 0,
    val rewards: List<Reward> = emptyList(),
)

class RedeemViewModel(
    rewardRepository: RewardRepository,
    ledgerRepository: PointsLedgerRepository,
    private val redeemReward: RedeemRewardUseCase,
    weekCalculator: WeekCalculator,
) : ViewModel() {

    private val weekId = weekCalculator.weekId()

    val state: StateFlow<RedeemUiState> = combine(
        rewardRepository.observeRewards(),
        ledgerRepository.observeBalance(),
    ) { rewards, balance ->
        RedeemUiState(balance = balance, rewards = rewards)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RedeemUiState())

    fun onRedeem(reward: Reward, onResult: (RedeemResult) -> Unit) = viewModelScope.launch {
        onResult(redeemReward(weekId, reward))
    }
}
