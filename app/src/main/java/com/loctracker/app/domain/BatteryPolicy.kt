package com.loctracker.app.domain

import com.google.android.gms.location.Priority

enum class PowerTier { FULL, STATIONARY, SAVER, CRITICAL }

data class BatteryInfo(val percent: Int, val charging: Boolean, val powerSave: Boolean)

data class LocationPlan(
    val tier: PowerTier,
    val priority: Int,
    val intervalMs: Long,
    val minDistanceM: Float,
)

/** Chooses how aggressively to sample GPS so long sessions do not drain the battery. */
object BatteryPolicy {
    private const val MIN_SAMPLE_S = 15
    private const val MAX_SAMPLE_S = 300

    fun plan(requestedIntervalSec: Int, battery: BatteryInfo, stationary: Boolean): LocationPlan {
        val base = requestedIntervalSec.coerceIn(MIN_SAMPLE_S, MAX_SAMPLE_S)
        val onBattery = !battery.charging
        return when {
            onBattery && battery.percent <= 5 -> LocationPlan(
                PowerTier.CRITICAL, Priority.PRIORITY_LOW_POWER, (base * 4L).coerceAtLeast(600) * 1000, 100f,
            )
            onBattery && (battery.percent <= 15 || battery.powerSave) -> LocationPlan(
                PowerTier.SAVER, Priority.PRIORITY_BALANCED_POWER_ACCURACY, base * 2L * 1000, 50f,
            )
            stationary -> LocationPlan(
                PowerTier.STATIONARY, Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                (base * 2L).coerceAtMost(600) * 1000, 25f,
            )
            else -> LocationPlan(PowerTier.FULL, Priority.PRIORITY_HIGH_ACCURACY, base * 1000L, 5f)
        }
    }
}
