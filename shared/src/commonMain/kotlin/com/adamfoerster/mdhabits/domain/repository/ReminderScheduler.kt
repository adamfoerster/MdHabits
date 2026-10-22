package com.adamfoerster.mdhabits.domain.repository

import com.adamfoerster.mdhabits.domain.model.ReminderPlan

/**
 * Hands task reminders to the platform's notification system (AlarmManager on Android,
 * UNUserNotificationCenter on iOS). Platforms without one bind [UnsupportedReminderScheduler], whose
 * [isSupported] is false and hides every reminder control.
 */
interface ReminderScheduler {
    val isSupported: Boolean

    /** Asks the user to allow notifications; true when they are (or already were) allowed. */
    suspend fun requestPermission(): Boolean

    /** Makes [plans] the only scheduled reminders: anything scheduled before and not in it is cancelled. */
    fun schedule(plans: List<ReminderPlan>)
}

/** The [ReminderScheduler] of platforms without notifications (Desktop, for now). */
object UnsupportedReminderScheduler : ReminderScheduler {
    override val isSupported = false
    override suspend fun requestPermission() = false
    override fun schedule(plans: List<ReminderPlan>) = Unit
}
