package com.adamfoerster.mdhabits.ui.screens.redeem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.domain.usecase.RedeemResult
import com.adamfoerster.mdhabits.ui.components.CoinIcon
import com.adamfoerster.mdhabits.ui.components.GlyphPlate
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RedeemScreen(viewModel: RedeemViewModel = koinViewModel()) {
    val strings = LocalStrings.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberToastState()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp)) {
                Kicker(strings.tabRedeem)
                Text(strings.redeemTitle, Modifier.padding(top = 2.dp), style = handStyle(34.sp))

                // Balance card.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Paper.ink)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            strings.currentBalance.uppercase(),
                            style = sansStyle(12.sp, Paper.onDarkFaded, FontWeight.Medium).copy(letterSpacing = 0.7.sp),
                        )
                        Text(
                            "${state.balance} ${strings.pointsSuffix}",
                            Modifier.padding(top = 2.dp),
                            style = handStyle(40.sp, Paper.onDark),
                        )
                    }
                    CoinIcon(size = 52.dp)
                }
            }

            // Reward cards.
            Column(
                Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.rewards.forEach { reward ->
                    val canRedeem = state.balance >= reward.pointCost
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .paperCard(radius = 16)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GlyphPlate("★", size = 46.dp, glyphSize = 24.sp)
                        Column(Modifier.weight(1f)) {
                            Text(reward.name, style = sansStyle(16.sp, Paper.ink, FontWeight.SemiBold))
                            Text(
                                if (canRedeem) strings.availableNow else strings.missing(reward.pointCost - state.balance),
                                style = sansStyle(12.sp, if (canRedeem) Paper.green else Paper.muted),
                            )
                        }
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (canRedeem) Paper.ink else Paper.inset)
                                .paperClick(enabled = canRedeem) {
                                    viewModel.onRedeem(reward) { result ->
                                        when (result) {
                                            is RedeemResult.Success -> toast.show(strings.redeemed(reward.name))
                                            is RedeemResult.InsufficientBalance -> toast.show(strings.insufficientBalance)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (canRedeem) "${strings.tradeLabel} · ${reward.pointCost}"
                                else "${reward.pointCost} ${strings.pointsSuffix}",
                                style = sansStyle(13.sp, if (canRedeem) Paper.onDark else Paper.faded, FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        }

        PaperToast(toast)
    }
}
