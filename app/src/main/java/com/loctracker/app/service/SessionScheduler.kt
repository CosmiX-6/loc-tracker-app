package com.loctracker.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import com.loctracker.app.data.Recipient
import com.loctracker.app.data.Repository
import com.loctracker.app.data.Trigger
import com.loctracker.app.domain.Schedule
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Arms one alarm per scheduled recipient for the start of its next window. When the alarm fires
 * (or after boot/edit) [evaluate] starts a session if a window is open and has not been served yet.
 */
class SessionScheduler(
    private val context: Context,
    private val repo: Repository,
    private val sessions: SessionManager,
    private val prefs: SharedPreferences,
) {
    suspend fun rescheduleAll() = repo.allRecipients().forEach { evaluate(it.id) }

    suspend fun evaluate(recipientId: Long) {
        val r = repo.recipient(recipientId)
        if (r == null || !r.enabled || !r.scheduleEnabled) { cancel(recipientId); return }
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val active = Schedule.active(r, now)
        if (active != null) {
            val startMs = active.start.toInstant().toEpochMilli()
            if (prefs.getLong(key(recipientId), 0L) < startMs) {
                prefs.edit().putLong(key(recipientId), startMs).apply()
                sessions.start(recipientId, Trigger.SCHEDULE, active.end.toInstant().toEpochMilli())
            }
        }
        arm(r, now)
    }

    private fun arm(r: Recipient, now: ZonedDateTime) {
        val next = Schedule.next(r, now) ?: return cancel(r.id)
        Alarms.set(context, next.start.toInstant().toEpochMilli(), pending(r.id))
    }

    fun cancel(recipientId: Long) = context.getSystemService(AlarmManager::class.java).cancel(pending(recipientId))

    private fun pending(recipientId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, SCHEDULE_BASE + recipientId.toInt(),
        AppReceiver.intent(context, AppReceiver.ACTION_SCHEDULE).putExtra(AppReceiver.EXTRA_ID, recipientId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun key(id: Long) = "window_served_$id"

    private companion object { const val SCHEDULE_BASE = 2_000_000 }
}
