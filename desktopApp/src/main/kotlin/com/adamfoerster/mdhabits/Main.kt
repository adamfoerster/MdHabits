package com.adamfoerster.mdhabits

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adamfoerster.mdhabits.di.initKoinOnce

fun main() {
    initKoinOnce()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MdHabits",
            state = rememberWindowState(size = DpSize(480.dp, 860.dp)),
        ) {
            App()
        }
    }
}
