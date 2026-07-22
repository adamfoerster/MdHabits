package com.adamfoerster.mdhabits

import androidx.compose.ui.window.ComposeUIViewController
import com.adamfoerster.mdhabits.di.initKoinOnce

fun MainViewController() = ComposeUIViewController {
    initKoinOnce()
    App()
}
