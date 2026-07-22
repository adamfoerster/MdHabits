package com.adamfoerster.mdhabits.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations. */

@Serializable data object OnboardingGraph
@Serializable data object OnbFolder
@Serializable data object OnbValues
@Serializable data object OnbTheme
@Serializable data object OnbTasks
@Serializable data object OnbPenalties
@Serializable data object OnbRewards

@Serializable data object MainGraph

// Bottom-tab roots.
@Serializable data object HomeTab
@Serializable data object RedeemTab
@Serializable data object RegisterTab
@Serializable data object SettingsTab

// Secondary destinations.
@Serializable data object ThemeScreenRoute
@Serializable data class ReviewRoute(val weekId: String)
