package com.loctracker.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipientDao {
    @Query("SELECT * FROM recipients ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Recipient>>

    @Query("SELECT * FROM recipients")
    suspend fun getAll(): List<Recipient>

    @Query("SELECT * FROM recipients WHERE id = :id")
    suspend fun get(id: Long): Recipient?

    @Upsert
    suspend fun upsert(recipient: Recipient): Long

    @Query("DELETE FROM recipients WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE state = 'ACTIVE' ORDER BY startedAt")
    fun observeActive(): Flow<List<ShareSession>>

    @Query("SELECT * FROM sessions WHERE state = 'ACTIVE'")
    suspend fun getActive(): List<ShareSession>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: Long): ShareSession?

    @Insert
    suspend fun insert(session: ShareSession): Long

    @Update
    suspend fun update(session: ShareSession)

    @Query("DELETE FROM sessions WHERE state = 'ENDED' AND endedAt < :before")
    suspend fun pruneEnded(before: Long)
}

@Dao
interface DeliveryDao {
    @Query("SELECT * FROM deliveries ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DeliveryRecord>>

    @Query("SELECT * FROM deliveries WHERE id = :id")
    suspend fun get(id: Long): DeliveryRecord?

    @Query("SELECT * FROM deliveries WHERE status IN ('QUEUED','WAITING_FIX','WAITING_NETWORK')")
    suspend fun getPending(): List<DeliveryRecord>

    @Insert
    suspend fun insert(record: DeliveryRecord): Long

    @Update
    suspend fun update(record: DeliveryRecord)

    @Query(
        "SELECT * FROM deliveries WHERE recipientId = :recipientId AND kind = :kind " +
            "AND status IN ('QUEUED','WAITING_FIX','WAITING_NETWORK','READY')",
    )
    suspend fun getOutstanding(recipientId: Long, kind: DeliveryKind): List<DeliveryRecord>

    @Query(
        "SELECT * FROM deliveries WHERE sessionId = :sessionId " +
            "AND status IN ('QUEUED','WAITING_FIX','WAITING_NETWORK','READY')",
    )
    suspend fun getOutstandingForSession(sessionId: Long): List<DeliveryRecord>

    @Query("SELECT * FROM deliveries WHERE sessionId = :sessionId AND kind = 'NATIVE_LIVE_HANDOFF' ORDER BY id DESC LIMIT 1")
    suspend fun findNative(sessionId: Long): DeliveryRecord?

    @Query("DELETE FROM deliveries WHERE createdAt < :before")
    suspend fun pruneOlderThan(before: Long)

    @Query("DELETE FROM deliveries")
    suspend fun deleteAll()
}
