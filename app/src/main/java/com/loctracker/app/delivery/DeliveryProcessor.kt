package com.loctracker.app.delivery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.loctracker.app.LocTrackerApp
import com.loctracker.app.data.DeliveryKind
import com.loctracker.app.data.DeliveryRecord
import com.loctracker.app.data.DeliveryStatus
import com.loctracker.app.data.Recipient
import com.loctracker.app.data.Repository
import com.loctracker.app.data.SessionState
import com.loctracker.app.data.ShareSession
import com.loctracker.app.location.LocationHub
import com.loctracker.app.service.Notifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Turns "a location update is due" into a ready-to-send WhatsApp prompt.
 *
 * Updates wait while there is no fresh GPS fix or no network (WhatsApp could not deliver the
 * message anyway), are retried with exponential backoff, and are marked FAILED after
 * [MAX_ATTEMPTS]. A newer update always supersedes an older unsent one so a stale position is
 * never sent.
 */
class DeliveryProcessor(
    private val context: Context,
    private val repo: Repository,
    private val hub: LocationHub,
    private val notifier: Notifier,
) {
    private val lock = Mutex()

    suspend fun queueLinkUpdate(session: ShareSession, recipient: Recipient) = lock.withLock {
        val now = System.currentTimeMillis()
        repo.outstanding(recipient.id, DeliveryKind.LOCATION_LINK).forEach {
            repo.updateDelivery(it.copy(status = DeliveryStatus.SKIPPED, updatedAt = now, note = "Replaced by a newer update"))
        }
        repo.insertDelivery(
            DeliveryRecord(
                sessionId = session.id, recipientId = recipient.id, recipientName = recipient.name,
                kind = DeliveryKind.LOCATION_LINK, status = DeliveryStatus.QUEUED, createdAt = now,
            ),
        )
        repo.updateSession(session.copy(lastLinkUpdateAt = now))
    }

    /** @return true if some updates are still waiting (so a retry should be scheduled). */
    suspend fun processPending(): Boolean = lock.withLock {
        val now = System.currentTimeMillis()
        var waiting = false
        var waitingForNetwork = false
        for (rec in repo.pendingDeliveries()) {
            val session = repo.session(rec.sessionId)
            if (session == null || session.state != SessionState.ACTIVE) {
                repo.updateDelivery(rec.copy(status = DeliveryStatus.SKIPPED, updatedAt = now, note = "Session ended"))
                continue
            }
            if (rec.nextAttemptAt > now) { waiting = true; continue }

            val fix = hub.fix.value
            val fresh = fix != null && now - fix.timeMs <= FRESH_FIX_MS
            val online = isOnline()
            if (fresh && online && fix != null) {
                repo.updateDelivery(
                    rec.copy(
                        status = DeliveryStatus.READY, updatedAt = now, lat = fix.lat, lon = fix.lon,
                        accuracyM = fix.accuracyM, note = null,
                    ),
                )
                notifier.postReady(rec.id, rec.recipientId, rec.recipientName, rec.kind)
                continue
            }
            val attempts = rec.attempts + 1
            val reason = if (!online) "No network connection" else "No recent GPS fix"
            if (attempts >= MAX_ATTEMPTS) {
                repo.updateDelivery(
                    rec.copy(status = DeliveryStatus.FAILED, attempts = attempts, updatedAt = now, note = "Gave up: $reason"),
                )
            } else {
                waiting = true
                if (!online) waitingForNetwork = true
                repo.updateDelivery(
                    rec.copy(
                        status = if (!online) DeliveryStatus.WAITING_NETWORK else DeliveryStatus.WAITING_FIX,
                        attempts = attempts, updatedAt = now, note = "$reason - retrying",
                        nextAttemptAt = now + backoffMs(attempts),
                    ),
                )
            }
        }
        if (waiting) scheduleRetry(waitingForNetwork)
        waiting
    }

    private fun scheduleRetry(needsNetwork: Boolean) {
        val req = OneTimeWorkRequestBuilder<RetryWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setInitialDelay(15, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("delivery-retry", ExistingWorkPolicy.KEEP, req)
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        const val FRESH_FIX_MS = 5 * 60_000L

        /** 15 s, 30 s, 60 s, 2 min, 4 min ... */
        fun backoffMs(attempt: Int): Long = 15_000L shl (attempt - 1).coerceIn(0, 6)
    }
}

class RetryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LocTrackerApp).container
        return if (container.deliveries.processPending() && runAttemptCount < 8) Result.retry() else Result.success()
    }
}
