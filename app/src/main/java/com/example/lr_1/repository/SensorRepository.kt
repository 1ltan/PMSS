package com.example.lr_1.repository

import com.example.lr_1.data.local.SensorRecord
import com.example.lr_1.data.local.SensorRecordDao
import com.example.lr_1.helpers.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoNetworkException : IllegalStateException("Немає підтвердженого інтернет-з’єднання")

class SensorRepository(
    private val api: CloudApi,
    private val networkMonitor: NetworkMonitor,
    private val dao: SensorRecordDao,
    private val uploadEndpoint: String,
    private val commandEndpoint: String,
) {
    fun observeLatest(limit: Int = 50): Flow<List<SensorRecord>> {
        require(limit > 0)
        return dao.observeLatest(limit)
    }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    suspend fun enqueue(reading: SensorReading): Long = withContext(Dispatchers.IO) {
        require(reading.value.isFinite())
        require(reading.x.isFinite() && reading.y.isFinite() && reading.z.isFinite())
        dao.insert(reading.toRecord())
    }

    suspend fun syncPending(limit: Int = MAX_SYNC_BATCH): Result<SyncSummary> = withContext(Dispatchers.IO) {
        require(limit > 0)
        val pending = dao.pending(limit)
        if (pending.isEmpty()) return@withContext Result.success(SyncSummary(0, 0))
        if (!networkMonitor.isOnline()) return@withContext Result.failure(NoNetworkException())

        var synced = 0
        try {
            for (record in pending) {
                val response = api.upload(uploadEndpoint, record.toPayload())
                check(response.isSuccessful) { "HTTP ${response.code()} під час синхронізації" }
                dao.markSynced(record.id)
                synced++
            }
            Result.success(SyncSummary(attempted = pending.size, synced = synced))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun readRemoteCommand(): Result<RemoteCommand> = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline()) return@withContext Result.failure(NoNetworkException())
        try {
            Result.success(api.readCommand(commandEndpoint).toDomainCommand())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) { dao.deleteAll() }

    companion object {
        const val MAX_SYNC_BATCH = 100
    }
}

data class SyncSummary(val attempted: Int, val synced: Int)
