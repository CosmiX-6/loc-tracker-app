package com.loctracker.app.location

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.loctracker.app.domain.BatteryInfo
import com.loctracker.app.domain.Fix
import com.loctracker.app.domain.GpsStatus
import com.loctracker.app.domain.GpsStatusCalculator
import com.loctracker.app.domain.PowerTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-wide, in-memory view of the device's location state. Nothing here is persisted. */
class LocationHub {
    private val _fix = MutableStateFlow<Fix?>(null)
    val fix: StateFlow<Fix?> = _fix.asStateFlow()

    private val _status = MutableStateFlow(GpsStatus.SEARCHING)
    val status: StateFlow<GpsStatus> = _status.asStateFlow()

    private val _tier = MutableStateFlow(PowerTier.FULL)
    val tier: StateFlow<PowerTier> = _tier.asStateFlow()

    private val _battery = MutableStateFlow(BatteryInfo(100, true, false))
    val battery: StateFlow<BatteryInfo> = _battery.asStateFlow()

    private val recent = ArrayDeque<Fix>()

    @Synchronized
    fun onLocation(l: Location) {
        val f = Fix(l.latitude, l.longitude, if (l.hasAccuracy()) l.accuracy else 999f, l.time, if (l.hasSpeed()) l.speed else 0f)
        _fix.value = f
        recent.addLast(f)
        while (recent.size > 6) recent.removeFirst()
    }

    /** Stationary = last few fixes cluster within 20 m over at least 90 s. */
    @Synchronized
    fun isStationary(): Boolean {
        if (recent.size < 3) return false
        val last = recent.last()
        if (last.timeMs - recent.first().timeMs < 90_000) return false
        val out = FloatArray(1)
        return recent.all {
            Location.distanceBetween(it.lat, it.lon, last.lat, last.lon, out)
            out[0] < 20f
        }
    }

    fun setTier(t: PowerTier) { _tier.value = t }

    fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val hasPerm = hasLocationPermission(context)
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        _status.value = GpsStatusCalculator.compute(hasPerm, LocationManagerCompat.isLocationEnabled(lm), _fix.value, nowMs)
        _battery.value = readBattery(context)
    }

    @Synchronized
    fun reset() {
        _fix.value = null
        recent.clear()
    }

    companion object {
        fun hasLocationPermission(c: Context) =
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun hasBackgroundPermission(c: Context) =
            ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun readBattery(c: Context): BatteryInfo {
            val i: Intent? = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
            val pct = if (level >= 0 && scale > 0) level * 100 / scale else 100
            return BatteryInfo(pct, charging, pm.isPowerSaveMode)
        }
    }
}
