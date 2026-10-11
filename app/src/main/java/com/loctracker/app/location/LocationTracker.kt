package com.loctracker.app.location

import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.loctracker.app.domain.LocationPlan

/** Thin wrapper over the fused provider; re-requests updates only when the plan changes. */
class LocationTracker(private val context: Context, private val hub: LocationHub) {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var current: LocationPlan? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(hub::onLocation)
            hub.refresh(context)
        }

        override fun onLocationAvailability(a: LocationAvailability) = hub.refresh(context)
    }

    /** @return false if location permission is missing. */
    fun apply(plan: LocationPlan): Boolean {
        if (plan == current) return true
        if (!LocationHub.hasLocationPermission(context)) return false
        val request = LocationRequest.Builder(plan.priority, plan.intervalMs)
            .setMinUpdateIntervalMillis(plan.intervalMs / 2)
            .setMinUpdateDistanceMeters(plan.minDistanceM)
            .setWaitForAccurateLocation(false)
            .build()
        return try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            current = plan
            hub.setTier(plan.tier)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun stop() {
        client.removeLocationUpdates(callback)
        current = null
    }
}
