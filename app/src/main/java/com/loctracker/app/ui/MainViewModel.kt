package com.loctracker.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Priority
import com.loctracker.app.LocTrackerApp
import com.loctracker.app.data.DeliveryRecord
import com.loctracker.app.data.EndReason
import com.loctracker.app.data.Recipient
import com.loctracker.app.data.ShareSession
import com.loctracker.app.data.Trigger
import com.loctracker.app.delivery.HandoffActivity
import com.loctracker.app.domain.BatteryInfo
import com.loctracker.app.domain.Fix
import com.loctracker.app.domain.GpsStatus
import com.loctracker.app.domain.LocationPlan
import com.loctracker.app.domain.PowerTier
import com.loctracker.app.location.LocationHub
import com.loctracker.app.location.LocationTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as LocTrackerApp).container
    private val lazily = SharingStarted.WhileSubscribed(5_000)

    val recipients: StateFlow<List<Recipient>> = c.repository.recipientsFlow.stateIn(viewModelScope, lazily, emptyList())
    val sessions: StateFlow<List<ShareSession>> = c.repository.activeSessionsFlow.stateIn(viewModelScope, lazily, emptyList())
    val history: StateFlow<List<DeliveryRecord>> = c.repository.historyFlow.stateIn(viewModelScope, lazily, emptyList())

    val fix: StateFlow<Fix?> = c.hub.fix
    val gps: StateFlow<GpsStatus> = c.hub.status
    val battery: StateFlow<BatteryInfo> = c.hub.battery
    val tier: StateFlow<PowerTier> = c.hub.tier

    val now: StateFlow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(5_000) } }
        .stateIn(viewModelScope, lazily, System.currentTimeMillis())

    // Light location preview while the map is on screen and no session is running.
    private val preview = LocationTracker(app, c.hub)
    private var previewWanted = false

    init {
        viewModelScope.launch {
            sessions.collect { applyPreview(it.isEmpty()) }
        }
        viewModelScope.launch {
            while (true) { c.hub.refresh(getApplication()); delay(3_000) }
        }
    }

    fun setPreviewWanted(wanted: Boolean) { previewWanted = wanted; applyPreview(sessions.value.isEmpty()) }

    private fun applyPreview(noSessions: Boolean) {
        if (previewWanted && noSessions) {
            preview.apply(LocationPlan(PowerTier.FULL, Priority.PRIORITY_BALANCED_POWER_ACCURACY, 8_000, 5f))
        } else preview.stop()
    }

    fun refreshStatus() = c.hub.refresh(getApplication())

    fun isOnboarded() = c.onboardingDone()
    fun setOnboarded() = c.setOnboardingDone()
    fun retentionDays() = c.retentionDays()
    fun setRetention(days: Int) = viewModelScope.launch { c.setRetentionDays(days); c.repository.prune(days) }

    suspend fun recipient(id: Long) = c.repository.recipient(id)

    fun save(r: Recipient, onSaved: () -> Unit) = viewModelScope.launch {
        val id = c.repository.saveRecipient(r)
        c.scheduler.evaluate(id)
        onSaved()
    }

    fun setEnabled(r: Recipient, enabled: Boolean) = viewModelScope.launch {
        c.repository.saveRecipient(r.copy(enabled = enabled))
        if (!enabled) c.sessions().firstOrNull { it.recipientId == r.id }?.let { c.sessionManager.stop(it.id, EndReason.STOPPED) }
        c.scheduler.evaluate(r.id)
    }

    fun delete(r: Recipient) = viewModelScope.launch {
        c.sessions().firstOrNull { it.recipientId == r.id }?.let { c.sessionManager.stop(it.id, EndReason.RECIPIENT_REMOVED) }
        c.scheduler.cancel(r.id)
        c.repository.deleteRecipient(r.id)
    }

    fun startSharing(ids: Collection<Long>) = viewModelScope.launch {
        ids.forEach { c.sessionManager.start(it, Trigger.MANUAL) }
    }

    fun stop(sessionId: Long) = viewModelScope.launch { c.sessionManager.stop(sessionId, EndReason.STOPPED) }
    fun stopAll() = viewModelScope.launch { c.sessionManager.stopAll() }
    fun confirmNativeLive(sessionId: Long) = viewModelScope.launch { c.sessionManager.confirmNativeLive(sessionId) }

    /** Opens the recipient's WhatsApp chat so the user can start Live Location there. */
    fun openWhatsApp(context: Context, session: ShareSession) = viewModelScope.launch {
        val id = c.sessionManager.ensureNativeRecord(session)
        context.startActivity(HandoffActivity.intent(context, id))
    }

    fun clearHistory() = viewModelScope.launch { c.repository.clearHistory() }
    fun wipeAll() = viewModelScope.launch {
        c.sessionManager.stopAll()
        c.repository.allRecipients().forEach { c.scheduler.cancel(it.id) }
        c.repository.wipeAll()
        c.prefs.edit().clear().apply()
    }

    override fun onCleared() { preview.stop() }
}

