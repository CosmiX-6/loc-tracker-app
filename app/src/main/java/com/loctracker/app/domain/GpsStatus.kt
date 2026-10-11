package com.loctracker.app.domain

enum class GpsStatus { NO_PERMISSION, GPS_OFF, SEARCHING, WEAK, GOOD, STALE }

data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Float,
    val timeMs: Long,
    val speedMps: Float = 0f,
)

object GpsStatusCalculator {
    const val WEAK_ACCURACY_M = 50f
    const val STALE_AFTER_MS = 2 * 60_000L

    fun compute(hasPermission: Boolean, locationEnabled: Boolean, fix: Fix?, nowMs: Long): GpsStatus = when {
        !hasPermission -> GpsStatus.NO_PERMISSION
        !locationEnabled -> GpsStatus.GPS_OFF
        fix == null -> GpsStatus.SEARCHING
        nowMs - fix.timeMs > STALE_AFTER_MS -> GpsStatus.STALE
        fix.accuracyM > WEAK_ACCURACY_M -> GpsStatus.WEAK
        else -> GpsStatus.GOOD
    }
}
