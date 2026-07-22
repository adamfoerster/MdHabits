package com.adamfoerster.mdhabits.ui.screens.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.ui.components.Chevron
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.PaperToast
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.paperCard
import com.adamfoerster.mdhabits.ui.components.paperClick
import com.adamfoerster.mdhabits.ui.components.rememberToastState
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.onboarding.NumberCircle
import com.adamfoerster.mdhabits.ui.theme.LocalPaperFonts
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ThemeScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = koinViewModel(),
) {
    val strings = LocalStrings.current
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val toast = rememberToastState()

    Box(Modifier.fillMaxSize().background(Paper.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // Back link.
            Row(
                Modifier
                    .padding(start = 20.dp, top = 6.dp, bottom = 8.dp)
                    .paperClick(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Chevron(left = true, color = Paper.subtle, size = 16.dp)
                Text(strings.back, style = sansStyle(13.sp, Paper.subtle, FontWeight.SemiBold))
            }

            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp)) {
                Kicker(strings.themeKicker(viewModel.year))
                Text(theme?.name ?: strings.noTheme, Modifier.padding(top = 4.dp), style = handStyle(44.sp))
                val description = theme?.description.orEmpty()
                if (description.isNotBlank()) {
                    Text(
                        description,
                        Modifier.padding(top = 10.dp),
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = LocalPaperFonts.current.serifItalic,
                            fontStyle = FontStyle.Italic,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = Paper.body,
                        ),
                    )
                }
            }

            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
                Text(strings.threeObjectivesHand, style = handStyle(20.sp, Paper.subtle))
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    theme?.objectives.orEmpty().forEachIndexed { index, objective ->
                        val earned = if (objective.achieved) objective.points else 0
                        val progress = if (objective.points > 0) earned.toFloat() / objective.points else 0f
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .paperCard(radius = 16)
                                .paperClick {
                                    viewModel.onToggleAchieved(objective)
                                    if (!objective.achieved) {
                                        toast.show("+${objective.points} ${strings.pointsWord} · ${objective.title}")
                                    }
                                }
                                .padding(18.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                NumberCircle(index + 1, size = 34.dp, fontSize = 15.sp)
                                Text(
                                    objective.title,
                                    Modifier.weight(1f),
                                    style = sansStyle(17.sp, Paper.ink, FontWeight.SemiBold),
                                )
                                Text("+${objective.points}", style = handStyle(18.sp, Paper.accent))
                            }
                            // Progress bar.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(Paper.progressTrack),
                            ) {
                                if (progress > 0f) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(progress)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Paper.greenBright),
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(
                                    strings.progressOf(earned, objective.points),
                                    Modifier.weight(1f),
                                    style = sansStyle(11.5.sp, Paper.muted),
                                )
                                Text(
                                    if (objective.achieved) strings.statusDone else "${(progress * 100).toInt()}%",
                                    style = sansStyle(
                                        11.5.sp,
                                        if (objective.achieved) Paper.green else Paper.muted,
                                        FontWeight.SemiBold,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        PaperToast(toast)
    }
}
