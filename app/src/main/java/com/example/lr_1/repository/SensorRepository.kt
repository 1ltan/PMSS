package com.example.lr_1.repository

import com.example.lr_1.helpers.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NoNetworkException : IllegalStateException("Немає підтвердженого інтернет-з’єднання")

class SensorRepository(
    private val api: CloudApi,
    private val networkMonitor: NetworkMonitor,
    private val uploadEndpoint: String,
    private val commandEndpoint: String,
) {
    suspend fun save(reading: SensorReading): Result<Unit> = withContext(Dispatchers.IO) {
        if (!networkMonitor.isOnline()) return@withContext Result.failure(NoNetworkException())
        try {
            val response = api.upload(uploadEndpoint, reading.toPayload())
            check(response.isSuccessful) { "HTTP ${response.code()} під час синхронізації" }
            Result.success(Unit)
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
}
