package com.adamfoerster.mdhabits.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.ui.components.Kicker
import com.adamfoerster.mdhabits.ui.components.PrimaryButton
import com.adamfoerster.mdhabits.ui.components.handStyle
import com.adamfoerster.mdhabits.ui.components.sansStyle
import com.adamfoerster.mdhabits.ui.theme.MdHabitsTheme
import com.adamfoerster.mdhabits.ui.theme.Paper
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

/**
 * Standalone root for the screen Health Connect opens when the user asks how MdHabits uses their
 * health data (Android's permissions-rationale / permission-usage intents land here).
 */
@Composable
fun HealthPrivacyApp(onClose: () -> Unit) = KoinContext {
    val lang by koinInject<LocaleController>().lang.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
        MdHabitsTheme {
            HealthPrivacyScreen(onClose)
        }
    }
}

@Composable
fun HealthPrivacyScreen(onClose: () -> Unit) {
    val strings = LocalStrings.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Kicker(strings.cfgHealth)
        Text(strings.healthPrivacyTitle, Modifier.padding(top = 2.dp), style = handStyle(34.sp))
        Text(
            strings.healthPrivacyBody,
            Modifier.padding(top = 16.dp),
            style = sansStyle(15.sp, Paper.body),
        )
        PrimaryButton(
            text = strings.close,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            onClick = onClose,
        )
    }
}
