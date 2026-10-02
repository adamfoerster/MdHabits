package com.adamfoerster.mdhabits.platform

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.adamfoerster.mdhabits.domain.model.DailyHealth
import com.adamfoerster.mdhabits.domain.repository.HealthAvailability
import com.adamfoerster.mdhabits.domain.repository.HealthDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

/**
 * Bridge between [MainActivity] (which owns the Health Connect permission launcher) and
 * [AndroidHealthConnectSource], following the same pattern as [AndroidVaultBridge].
 */
object AndroidHealthBridge {
    /** Set by MainActivity to launch Health Connect's permission request for [permissions]. */
    var launchPermissionRequest: ((permissions: Set<String>) -> Unit)? = null

    /** Awaited by [AndroidHealthConnectSource.requestPermissions]; completed from the activity result. */
    var pending: CompletableDeferred<Set<String>>? = null

    /** Called by MainActivity with the permissions the user granted (empty when cancelled). */
    fun onPermissionsResult(granted: Set<String>) {
        pending?.complete(granted)
        pending = null
    }
}

/** Reads steps, sleep, and weight from Health Connect. Read-only: the app never writes health data. */
class AndroidHealthConnectSource(private val context: Context) : HealthDataSource {

    override val isSupported = true

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): HealthAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
            // Android 13 and older need the Health Connect app installed (or updated) from the store.
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.NOT_INSTALLED
            else -> HealthAvailability.UNSUPPORTED
        }

    override suspend fun hasPermissions(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        return client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)
    }

    override suspend fun requestPermissions(): Boolean {
        if (availability() != HealthAvailability.AVAILABLE) return false
        if (hasPermissions()) return true
        val launch = AndroidHealthBridge.launchPermissionRequest ?: return false
        val deferred = CompletableDeferred<Set<String>>()
        AndroidHealthBridge.pending = deferred
        launch(PERMISSIONS)
        return deferred.await().containsAll(PERMISSIONS)
    }

    override suspend fun readDay(date: LocalDate): DailyHealth {
        val zone = ZoneId.systemDefault()
        val day = java.time.LocalDate.parse(date.toString())
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return DailyHealth(
            date = date,
            steps = readSteps(start, end),
            sleepMinutes = readSleepMinutes(day.minusDays(1).atStartOfDay(zone).toInstant(), start, end),
            weightKg = readWeightKg(start, end),
        )
    }

    /** The aggregate API de-duplicates steps reported by several apps (phone + watch). */
    private suspend fun readSteps(start: Instant, end: Instant): Long? =
        client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, end)),
        )[StepsRecord.COUNT_TOTAL]

    /**
     * The sleep of the night that ended on the day: every session ending within it, minus the
     * stages spent awake. Reading from the day before catches sessions that began before midnight.
     */
    private suspend fun readSleepMinutes(from: Instant, start: Instant, end: Instant): Long? {
        val sessions = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(from, end)),
        ).records.filter { it.endTime >= start && it.endTime < end }
        if (sessions.isEmpty()) return null
        return sessions.sumOf { session ->
            val awake = session.stages
                .filter { it.stage in AWAKE_STAGES }
                .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            Duration.between(session.startTime, session.endTime).toMinutes() - awake
        }.coerceAtLeast(0)
    }

    /** The day's latest weigh-in, rounded to 0.1 kg so the week note stays readable. */
    private suspend fun readWeightKg(start: Instant, end: Instant): Double? =
        client.readRecords(
            ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.between(start, end)),
        ).records.maxByOrNull { it.time }
            ?.weight?.inKilograms
            ?.let { (it * 10).roundToLong() / 10.0 }

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class),
        )

        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )
    }
}
