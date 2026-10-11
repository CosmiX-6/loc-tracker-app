package com.loctracker.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Single entry point to persisted state so storage can be swapped (e.g. sync) later. */
class Repository(private val db: AppDatabase) {
    private val recipients get() = db.recipients()
    private val sessions get() = db.sessions()
    private val deliveries get() = db.deliveries()

    val recipientsFlow: Flow<List<Recipient>> get() = recipients.observeAll()
    val activeSessionsFlow: Flow<List<ShareSession>> get() = sessions.observeActive()
    val historyFlow: Flow<List<DeliveryRecord>> get() = deliveries.observeRecent(300)

    suspend fun allRecipients() = recipients.getAll()
    suspend fun recipient(id: Long) = recipients.get(id)
    suspend fun saveRecipient(r: Recipient): Long {
        val id = recipients.upsert(r)
        return if (r.id == 0L) id else r.id
    }
    suspend fun deleteRecipient(id: Long) = recipients.delete(id)

    suspend fun activeSessions() = sessions.getActive()
    suspend fun session(id: Long) = sessions.get(id)
    suspend fun insertSession(s: ShareSession) = sessions.insert(s)
    suspend fun updateSession(s: ShareSession) = sessions.update(s)

    suspend fun delivery(id: Long) = deliveries.get(id)
    suspend fun pendingDeliveries() = deliveries.getPending()
    suspend fun insertDelivery(d: DeliveryRecord) = deliveries.insert(d)
    suspend fun updateDelivery(d: DeliveryRecord) = deliveries.update(d)
    suspend fun outstanding(recipientId: Long, kind: DeliveryKind) = deliveries.getOutstanding(recipientId, kind)
    suspend fun outstandingForSession(sessionId: Long) = deliveries.getOutstandingForSession(sessionId)

    suspend fun nativeRecord(sessionId: Long) = deliveries.findNative(sessionId)

    suspend fun prune(retentionDays: Int) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 3_600_000L
        deliveries.pruneOlderThan(cutoff)
        sessions.pruneEnded(cutoff)
    }

    suspend fun clearHistory() = deliveries.deleteAll()

    /** Irreversibly removes everything the app has stored. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
