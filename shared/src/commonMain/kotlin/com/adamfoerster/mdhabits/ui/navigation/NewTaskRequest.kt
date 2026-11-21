package com.adamfoerster.mdhabits.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A request from outside the UI (the Android home-screen widget's "+" button) to land on the new-task
 * form. [MainScaffold] switches to the Register tab while it is pending; the Register screen opens the
 * form and clears it.
 */
object NewTaskRequest {
    val pending = MutableStateFlow(false)
}
