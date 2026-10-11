package com.loctracker.app

import android.app.Application
import android.content.Context
import com.loctracker.app.data.AppDatabase
import com.loctracker.app.data.Repository
import com.loctracker.app.delivery.DeliveryProcessor
import com.loctracker.app.location.LocationHub
import com.loctracker.app.service.Notifier
import com.loctracker.app.service.SessionManager
import com.loctracker.app.service.SessionScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependency graph; small enough that a DI framework is not worth its weight yet. */
class AppContainer(private val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs by lazy { app.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val hub = LocationHub()
    val notifier by lazy { Notifier(app) }
    val repository by lazy { Repository(AppDatabase.create(app)) }
    val sessionManager by lazy { SessionManager(app, repository, notifier) }
    val scheduler by lazy { SessionScheduler(app, repository, sessionManager, prefs) }
    val deliveries by lazy { DeliveryProcessor(app, repository, hub, notifier) }

    suspend fun sessions() = repository.activeSessions()

    fun retentionDays() = prefs.getInt(KEY_RETENTION, 14)
    fun setRetentionDays(days: Int) = prefs.edit().putInt(KEY_RETENTION, days).apply()
    fun onboardingDone() = prefs.getBoolean(KEY_ONBOARDED, false)
    fun setOnboardingDone() = prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()

    private companion object {
        const val KEY_RETENTION = "retention_days"
        const val KEY_ONBOARDED = "onboarded"
    }
}
