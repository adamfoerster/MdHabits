package com.adamfoerster.mdhabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.adamfoerster.mdhabits.di.initKoinOnce
import com.adamfoerster.mdhabits.platform.AndroidVaultBridge
import com.adamfoerster.mdhabits.ui.screens.health.HealthPrivacyApp

/**
 * Shown by Health Connect (and, on Android 14+, by the system's health permission settings) when
 * the user asks why MdHabits wants their health data. Health Connect requires an app to have it.
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidVaultBridge.appContext = applicationContext
        initKoinOnce()

        setContent {
            HealthPrivacyApp(onClose = ::finish)
        }
    }
}
