package com.adamfoerster.mdhabits.platform

import com.adamfoerster.mdhabits.domain.model.ReminderPlan
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import kotlinx.datetime.isoDayNumber
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Schedules each task reminder as a repeating weekly local notification (one per due weekday).
 * iOS repeats these by itself, so — unlike Android — a task already done today still notifies today.
 */
class IosReminderScheduler : ReminderScheduler {

    override val isSupported = true

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun requestPermission(): Boolean = suspendCoroutine { continuation ->
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            continuation.resume(granted)
        }
    }

    override fun schedule(plans: List<ReminderPlan>) {
        center.removeAllPendingNotificationRequests()
        plans.forEach { plan ->
            plan.days.forEach { day ->
                val components = NSDateComponents().apply {
                    // Apple's weekday runs Sunday = 1 .. Saturday = 7; ISO runs Monday = 1 .. Sunday = 7.
                    weekday = (day.isoDayNumber % 7 + 1).toLong()
                    hour = plan.time.hour.toLong()
                    minute = plan.time.minute.toLong()
                }
                val content = UNMutableNotificationContent().apply {
                    setTitle(plan.title)
                    setBody(plan.body)
                    setSound(UNNotificationSound.defaultSound)
                }
                val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
                val request = UNNotificationRequest.requestWithIdentifier("${plan.taskId}-${day.name}", content, trigger)
                center.addNotificationRequest(request, withCompletionHandler = null)
            }
        }
    }
}
