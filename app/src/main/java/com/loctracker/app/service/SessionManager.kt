package com.loctracker.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.loctracker.app.R
import com.loctracker.app.data.DeliveryKind
import com.loctracker.app.data.DeliveryRecord
import com.loctracker.app.data.DeliveryStatus
import com.loctracker.app.data.EndReason
import com.loctracker.app.data.Repository
import com.loctracker.app.data.SessionState
import com.loctracker.app.data.ShareSession
import com.loctracker.app.data.Trigger
import com.loctracker.app.domain.WhatsAppLive

/** Owns the lifecycle of sharing sessions: start, stop and automatic expiry. */
class SessionManager(
    private val context: Context,
    private val repo: Repository,
    private val notifier: Notifier,
) {
    /**
     * Starts a session for [recipientId]. Returns the session id, or null if the recipient is
     * missing/disabled. An already-running session for the same recipient is reused.
     */
    suspend fun start(recipientId: Long, trigger: Trigger, windowEndMs: Long? = null): Long? {
        val recipient = repo.recipient(recipientId)?.takeIf { it.enabled } ?: return null
        repo.activeSessions().firstOrNull { it.recipientId == recipientId }?.let { return it.id }

        val now = System.currentTimeMillis()
        var expires = now + recipient.sessionMinutes * 60_000L
        if (windowEndMs != null) expires = minOf(expires, windowEndMs)
        val id = repo.insertSession(
            ShareSession(
                recipientId = recipientId, recipientName = recipient.name,
                startedAt = now, expiresAt = expires, trigger = trigger,
            ),
        )
        armExpiry(id, expires)
        val nativeId = repo.insertDelivery(
            DeliveryRecord(
                sessionId = id, recipientId = recipientId, recipientName = recipient.name,
                kind = DeliveryKind.NATIVE_LIVE_HANDOFF, status = DeliveryStatus.READY, createdAt = now,
            ),
        )
        // For scheduled starts the user is not looking at the app, so prompt them.
        if (trigger == Trigger.SCHEDULE) notifier.postReady(nativeId, recipientId, recipient.name, DeliveryKind.NATIVE_LIVE_HANDOFF)
        startService()
        return id
    }

    suspend fun stop(sessionId: Long, reason: EndReason) {
        val s = repo.session(sessionId)?.takeIf { it.state == SessionState.ACTIVE } ?: return
        val now = System.currentTimeMillis()
        repo.updateSession(s.copy(state = SessionState.ENDED, endedAt = now, endReason = reason))
        cancelExpiry(sessionId)
        repo.outstandingForSession(sessionId).forEach {
            repo.updateDelivery(it.copy(status = DeliveryStatus.SKIPPED, updatedAt = now, note = "Session ended"))
            notifier.cancelReady(it.recipientId, it.kind)
        }
        val liveRunning = (s.nativeLiveEndsAt ?: 0) > now
        if (reason == EndReason.EXPIRED || liveRunning) {
            val text = context.getString(if (liveRunning) R.string.n_ended_live else R.string.n_ended_plain)
            notifier.postEvent(Notifier.eventId(sessionId, 0), context.getString(R.string.n_ended_title, s.recipientName), text)
        }
    }

    suspend fun stopAll(reason: EndReason = EndReason.STOPPED) = repo.activeSessions().forEach { stop(it.id, reason) }

    suspend fun expireDue() {
        val now = System.currentTimeMillis()
        repo.activeSessions().filter { it.expiresAt <= now }.forEach { stop(it.id, EndReason.EXPIRED) }
    }

    /** The user confirms they started WhatsApp Live Location in the chat. */
    suspend fun confirmNativeLive(sessionId: Long) {
        val s = repo.session(sessionId) ?: return
        val r = repo.recipient(s.recipientId)
        val now = System.currentTimeMillis()
        val minutes = WhatsAppLive.optionFor(r?.sessionMinutes ?: 60)
        repo.updateSession(s.copy(nativeLiveStartedAt = now, nativeLiveEndsAt = now + minutes * 60_000L, nativeReminderSent = false))
    }

    /** Returns a hand-off record the UI can open, creating a new one if the old one is spent. */
    suspend fun ensureNativeRecord(session: ShareSession): Long {
        repo.nativeRecord(session.id)?.let {
            if (it.status == DeliveryStatus.READY || it.status == DeliveryStatus.OPENED_IN_WHATSAPP) return it.id
        }
        return repo.insertDelivery(
            DeliveryRecord(
                sessionId = session.id, recipientId = session.recipientId, recipientName = session.recipientName,
                kind = DeliveryKind.NATIVE_LIVE_HANDOFF, status = DeliveryStatus.READY, createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Re-arms expiry alarms and the foreground service after boot / app update / process death. */
    suspend fun resume() {
        expireDue()
        val active = repo.activeSessions()
        active.forEach { armExpiry(it.id, it.expiresAt) }
        if (active.isNotEmpty()) startService()
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
        } catch (e: Exception) {
            // Android refused a background foreground-service start (e.g. background location not granted).
            notifier.postEvent(
                Notifier.eventId(0, 1), context.getString(R.string.n_resume_title), context.getString(R.string.n_resume_text),
            )
        }
    }

    private fun expiryIntent(sessionId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, EXPIRY_BASE + sessionId.toInt(),
        AppReceiver.intent(context, AppReceiver.ACTION_EXPIRE).putExtra(AppReceiver.EXTRA_ID, sessionId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun armExpiry(sessionId: Long, atMs: Long) = Alarms.set(context, atMs, expiryIntent(sessionId))
    private fun cancelExpiry(sessionId: Long) = context.getSystemService(AlarmManager::class.java).cancel(expiryIntent(sessionId))

    private companion object { const val EXPIRY_BASE = 1_000_000 }
}

object Alarms {
    /** Exact when the user allowed it, otherwise a best-effort alarm that still fires in Doze. */
    fun set(context: Context, atMs: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }
}
