package com.loctracker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.loctracker.app.LocTrackerApp
import com.loctracker.app.data.EndReason
import kotlinx.coroutines.launch

/** Internal (non-exported) receiver for alarms and notification actions. */
class AppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LocTrackerApp).container
        val id = intent.getLongExtra(EXTRA_ID, 0)
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_STOP_ALL -> container.sessionManager.stopAll()
                    ACTION_EXPIRE -> container.sessionManager.stop(id, EndReason.EXPIRED)
                    ACTION_SCHEDULE -> container.scheduler.evaluate(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_STOP_ALL = "com.loctracker.app.STOP_ALL"
        const val ACTION_EXPIRE = "com.loctracker.app.EXPIRE"
        const val ACTION_SCHEDULE = "com.loctracker.app.SCHEDULE"
        const val EXTRA_ID = "id"

        fun intent(context: Context, action: String) =
            Intent(context, AppReceiver::class.java).setAction(action)
    }
}

/** Re-arms schedules and resumes unexpired sessions after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as LocTrackerApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.sessionManager.resume()
                container.scheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
