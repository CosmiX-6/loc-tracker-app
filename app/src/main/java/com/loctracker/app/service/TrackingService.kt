package com.loctracker.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.loctracker.app.LocTrackerApp
import com.loctracker.app.R
import com.loctracker.app.domain.BatteryPolicy
import com.loctracker.app.domain.PowerTier
import com.loctracker.app.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps GPS running while any session is active, queues interval-based
 * link updates, enforces expiry and applies the battery policy. It stops itself when idle.
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private lateinit var tracker: LocationTracker

    private val container get() = (application as LocTrackerApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tracker = LocationTracker(this, container.hub)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, Notifier.ID_TRACKING,
            container.notifier.trackingNotification(0, getString(R.string.tracking_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        if (loop == null) loop = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val c = container
        var lastPrune = 0L
        while (scope.isActive) {
            c.sessionManager.expireDue()
            val active = c.repository.activeSessions()
            if (active.isEmpty()) break

            val now = System.currentTimeMillis()
            val recipients = c.repository.allRecipients().associateBy { it.id }
            c.hub.refresh(this)

            val requestedSec = active.mapNotNull { recipients[it.recipientId]?.intervalMinutes }.minOrNull()?.times(60) ?: 60
            val plan = BatteryPolicy.plan(requestedSec, c.hub.battery.value, c.hub.isStationary())
            if (!tracker.apply(plan)) c.hub.refresh(this)

            for (s in active) {
                val r = recipients[s.recipientId] ?: continue
                val due = s.lastLinkUpdateAt?.let { now - it >= r.intervalMinutes * 60_000L } ?: true
                if (r.linkUpdates && due) c.deliveries.queueLinkUpdate(s, r)

                val liveEnds = s.nativeLiveEndsAt
                if (liveEnds != null && !s.nativeReminderSent && now >= liveEnds - 5 * 60_000L && liveEnds < s.expiresAt) {
                    c.repository.updateSession(s.copy(nativeReminderSent = true))
                    c.notifier.postEvent(
                        Notifier.eventId(s.id, 2),
                        getString(R.string.n_renew_title, s.recipientName), getString(R.string.n_renew_text),
                    )
                }
            }
            c.deliveries.processPending()

            val detail = when (plan.tier) {
                PowerTier.FULL -> getString(R.string.tier_full)
                PowerTier.STATIONARY -> getString(R.string.tier_stationary)
                PowerTier.SAVER -> getString(R.string.tier_saver)
                PowerTier.CRITICAL -> getString(R.string.tier_critical)
            }
            c.notifier.updateTracking(active.size, detail)

            if (now - lastPrune > 6 * 3_600_000L) {
                lastPrune = now
                c.repository.prune(c.retentionDays())
            }
            delay(10_000)
        }
        stopSelf()
    }

    override fun onDestroy() {
        tracker.stop()
        container.hub.reset()
        scope.cancel()
        super.onDestroy()
    }
}
