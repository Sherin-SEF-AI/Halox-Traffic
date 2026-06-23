package com.haloxtraffic.core.sync

import com.haloxtraffic.core.data.dao.SyncQueueDao
import com.haloxtraffic.core.data.entity.SyncQueueItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable, idempotent outbound queue (§13). Entities are enqueued by client UUID; the [SyncWorker]
 * drains pending items with retry/backoff and surfaces errors in the Sync screen. Append-only domain
 * objects (cases, evidence) are safe to re-send because the server upserts by id.
 */
@Singleton
class SyncQueue @Inject constructor(
    private val dao: SyncQueueDao,
) {
    val pendingCount: Flow<Int> = dao.observePendingCount()

    suspend fun enqueue(item: SyncQueueItemEntity) = dao.enqueue(item)
    suspend fun pending(limit: Int = 50): List<SyncQueueItemEntity> = dao.pending(limit)
    suspend fun markSynced(id: String, ts: Long) = dao.markSynced(id, ts)
    suspend fun markRetry(id: String, error: String) = dao.markRetry(id, error)
}
