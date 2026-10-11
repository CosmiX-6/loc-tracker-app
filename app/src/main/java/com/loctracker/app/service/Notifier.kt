package com.loctracker.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.loctracker.app.MainActivity
import com.loctracker.app.R
import com.loctracker.app.data.DeliveryKind
import com.loctracker.app.delivery.HandoffActivity

class Notifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val sys = context.getSystemService(NotificationManager::class.java)
        sys.createNotificationChannel(
            NotificationChannel(CH_TRACKING, context.getString(R.string.ch_tracking), NotificationManager.IMPORTANCE_LOW),
        )
        sys.createNotificationChannel(
            NotificationChannel(CH_HANDOFF, context.getString(R.string.ch_handoff), NotificationManager.IMPORTANCE_HIGH),
        )
        sys.createNotificationChannel(
            NotificationChannel(CH_EVENTS, context.getString(R.string.ch_events), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun trackingNotification(activeCount: Int, detail: String): Notification {
        val stop = PendingIntent.getBroadcast(
            context, 1, AppReceiver.intent(context, AppReceiver.ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CH_TRACKING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.tracking_title, activeCount, activeCount))
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.stop_all), stop)
            .build()
    }

    fun updateTracking(activeCount: Int, detail: String) =
        notify(ID_TRACKING, trackingNotification(activeCount, detail))

    /** "Tap to open WhatsApp" prompt. The user must complete the send / live-share in WhatsApp. */
    fun postReady(recordId: Long, recipientId: Long, recipientName: String, kind: DeliveryKind) {
        val open = PendingIntent.getActivity(
            context, 100 + recordId.toInt(), HandoffActivity.intent(context, recordId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (kind) {
            DeliveryKind.NATIVE_LIVE_HANDOFF ->
                context.getString(R.string.n_live_title, recipientName) to context.getString(R.string.n_live_text)
            DeliveryKind.LOCATION_LINK ->
                context.getString(R.string.n_link_title, recipientName) to context.getString(R.string.n_link_text)
        }
        val n = NotificationCompat.Builder(context, CH_HANDOFF)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        notify(readyId(recipientId, kind), n)
    }

    fun cancelReady(recipientId: Long, kind: DeliveryKind) = nm.cancel(readyId(recipientId, kind))

    fun postEvent(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        notify(id, n)
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, n: Notification) {
        if (nm.areNotificationsEnabled()) {
            try { nm.notify(id, n) } catch (_: SecurityException) { /* permission revoked mid-flight */ }
        }
    }

    companion object {
        const val CH_TRACKING = "tracking"
        const val CH_HANDOFF = "handoff"
        const val CH_EVENTS = "events"
        const val ID_TRACKING = 1
        private fun readyId(recipientId: Long, kind: DeliveryKind) = 10_000 + recipientId.toInt() * 4 + kind.ordinal
        fun eventId(sessionId: Long, salt: Int) = 500_000 + sessionId.toInt() * 4 + salt
    }
}
