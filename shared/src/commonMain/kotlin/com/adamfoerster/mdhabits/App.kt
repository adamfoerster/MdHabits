package com.adamfoerster.mdhabits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.adamfoerster.mdhabits.core.i18n.LocalStrings
import com.adamfoerster.mdhabits.core.i18n.LocaleController
import com.adamfoerster.mdhabits.core.i18n.stringsFor
import com.adamfoerster.mdhabits.core.settings.AppSettings
import com.adamfoerster.mdhabits.data.markdown.VaultWatcher
import com.adamfoerster.mdhabits.ui.navigation.MainGraph
import com.adamfoerster.mdhabits.ui.navigation.MainScaffold
import com.adamfoerster.mdhabits.ui.navigation.OnboardingGraph
import com.adamfoerster.mdhabits.ui.onboarding.OnboardingFlow
import com.adamfoerster.mdhabits.ui.theme.MdHabitsTheme
import org.koin.compose.KoinContext
import org.koin.compose.koinInject

@Composable
fun App() = KoinContext {
    val localeController = koinInject<LocaleController>()
    val settings = koinInject<AppSettings>()
    val lang by localeController.lang.collectAsStateWithLifecycle()
    val startOnboarded = remember { settings.onboardingComplete }
    val vaultWatcher = koinInject<VaultWatcher>()
    val lifecycleOwner = LocalLifecycleOwner.current

    // While the app is in the foreground, keep the loaded notes in step with the vault files so
    // changes synced from another device (e.g. by Syncthing) show up without a restart.
    LaunchedEffect(lifecycleOwner, vaultWatcher) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { vaultWatcher.watch() }
    }

    CompositionLocalProvider(LocalStrings provides stringsFor(lang)) {
        MdHabitsTheme {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = if (startOnboarded) MainGraph else OnboardingGraph,
            ) {
                composable<OnboardingGraph> {
                    OnboardingFlow(
                        onDone = {
                            navController.navigate(MainGraph) {
                                popUpTo(OnboardingGraph) { inclusive = true }
                            }
                        },
                    )
                }
                composable<MainGraph> {
                    MainScaffold()
                }
            }
        }
    }
}
