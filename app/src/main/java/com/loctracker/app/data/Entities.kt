package com.loctracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A person who receives location shares. Each recipient has fully independent settings. */
@Entity(tableName = "recipients")
data class Recipient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Digits only, international format without "+" (e.g. 491701234567). */
    val phone: String,
    val enabled: Boolean = true,
    /** How often a fresh location link is prepared for this recipient. */
    val intervalMinutes: Int = 10,
    /** Also prepare periodic map-link messages in addition to WhatsApp Live Location. */
    val linkUpdates: Boolean = true,
    val scheduleEnabled: Boolean = false,
    /** Bit mask, Monday = 1 ... Sunday = 64. */
    val scheduleDays: Int = 0b0011111,
    val scheduleStartMinute: Int = 8 * 60,
    val scheduleEndMinute: Int = 18 * 60,
    /** Maximum length of one sharing session; it expires automatically. */
    val sessionMinutes: Int = 60,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class SessionState { ACTIVE, ENDED }
enum class EndReason { EXPIRED, STOPPED, RECIPIENT_REMOVED }
enum class Trigger { MANUAL, SCHEDULE }

@Entity(tableName = "sessions")
data class ShareSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipientId: Long,
    val recipientName: String,
    val startedAt: Long,
    val expiresAt: Long,
    val state: SessionState = SessionState.ACTIVE,
    val endedAt: Long? = null,
    val endReason: EndReason? = null,
    val trigger: Trigger = Trigger.MANUAL,
    /** Set when the user confirms they started WhatsApp Live Location in the chat. */
    val nativeLiveStartedAt: Long? = null,
    val nativeLiveEndsAt: Long? = null,
    val lastLinkUpdateAt: Long? = null,
    val nativeReminderSent: Boolean = false,
)

enum class DeliveryKind { NATIVE_LIVE_HANDOFF, LOCATION_LINK }

enum class DeliveryStatus {
    QUEUED,
    WAITING_FIX,
    WAITING_NETWORK,
    READY,
    OPENED_IN_WHATSAPP,
    SKIPPED,
    FAILED,
}

@Entity(tableName = "deliveries")
data class DeliveryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val recipientId: Long,
    val recipientName: String,
    val kind: DeliveryKind,
    val status: DeliveryStatus,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracyM: Float? = null,
    val note: String? = null,
)
