package com.adamfoerster.mdhabits.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.adamfoerster.mdhabits.domain.model.ReminderPlan
import com.adamfoerster.mdhabits.domain.model.ReminderTime
import com.adamfoerster.mdhabits.domain.model.nextReminderAt
import com.adamfoerster.mdhabits.domain.repository.ReminderScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge between [MainActivity] (which owns the notification permission launcher, Android 13+) and
 * [AndroidReminderScheduler], following the same pattern as [AndroidHealthBridge].
 */
object AndroidNotificationBridge {
    /** Set by MainActivity to launch the POST_NOTIFICATIONS request. */
    var launchPermissionRequest: (() -> Unit)? = null

    /** Awaited by [AndroidReminderScheduler.requestPermission]; completed from the activity result. */
    var pending: CompletableDeferred<Boolean>? = null

    fun onPermissionResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

/** Schedules task reminders as one-shot alarms; [ReminderReceiver] posts the notification and sets the next one. */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    override val isSupported = true

    override suspend fun requestPermission(): Boolean {
        if (notificationsAllowed()) return true
        // Before Android 13 there is no runtime prompt: notifications are switched off in system settings.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val launch = AndroidNotificationBridge.launchPermissionRequest ?: return false
        val deferred = CompletableDeferred<Boolean>()
        AndroidNotificationBridge.pending = deferred
        launch()
        return deferred.await()
    }

    override fun schedule(plans: List<ReminderPlan>) = ReminderAlarms.replaceAll(context, plans)

    private fun notificationsAllowed(): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimeOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return runtimeOk && manager.areNotificationsEnabled()
    }
}

internal object ReminderAlarms {
    private const val PREFS = "mdhabits_reminders"
    private const val KEY_PLANS = "plans"
    const val ACTION_REMIND = "com.adamfoerster.mdhabits.REMIND"
    const val EXTRA_TASK_ID = "task_id"
    const val CHANNEL_ID = "task_reminders"

    /** Makes [plans] the only scheduled reminders and remembers them for the receiver and for reboots. */
    fun replaceAll(context: Context, plans: List<ReminderPlan>) {
        load(context).forEach { cancel(context, it.taskId) }
        save(context, plans)
        plans.forEach { arm(context, it) }
    }

    /** Sets the alarm for [plan]'s next occurrence; nothing when it has none. */
    fun arm(context: Context, plan: ReminderPlan) {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        val at = nextReminderAt(now, plan) ?: return
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toInstant(zone).toEpochMilliseconds(),
            pendingIntent(context, plan.taskId),
        )
    }

    fun cancel(context: Context, taskId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, taskId))
    }

    fun find(context: Context, taskId: String): ReminderPlan? = load(context).find { it.taskId == taskId }

    /** Re-arms every remembered reminder, e.g. after a reboot cleared the alarms. */
    fun rearmAll(context: Context) = load(context).forEach { arm(context, it.copy(skipToday = false)) }

    private fun pendingIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .putExtra(EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun save(context: Context, plans: List<ReminderPlan>) {
        val array = JSONArray()
        plans.forEach { plan ->
            array.put(
                JSONObject()
                    .put("id", plan.taskId)
                    .put("title", plan.title)
                    .put("body", plan.body)
                    .put("time", plan.time.toString())
                    .put("days", JSONArray(plan.days.map { it.name })),
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PLANS, array.toString()).apply()
    }

    private fun load(context: Context): List<ReminderPlan> = runCatching {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PLANS, null)
            ?: return emptyList()
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)
            val days = obj.getJSONArray("days")
            ReminderPlan(
                taskId = obj.getString("id"),
                title = obj.getString("title"),
                body = obj.getString("body"),
                time = ReminderTime.parseOrNull(obj.getString("time")) ?: return@mapNotNull null,
                days = (0 until days.length()).mapNotNull { i ->
                    DayOfWeek.entries.find { it.name == days.getString(i) }
                }.toSet(),
            )
        }
    }.getOrDefault(emptyList())
}

/** Posts the notification of a fired reminder, then arms the one after it. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderAlarms.EXTRA_TASK_ID) ?: return
        val plan = ReminderAlarms.find(context, taskId) ?: return
        post(context, plan)
        ReminderAlarms.arm(context, plan)
    }

    private fun post(context: Context, plan: ReminderPlan) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(ReminderAlarms.CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = android.app.Notification.Builder(context, ReminderAlarms.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(plan.title)
            .setContentText(plan.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(plan.taskId.hashCode(), notification) }
    }
}

/** Re-arms the reminders after the device restarts. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReminderAlarms.rearmAll(context)
    }
}
