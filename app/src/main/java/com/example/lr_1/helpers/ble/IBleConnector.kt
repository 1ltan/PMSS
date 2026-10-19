package com.example.lr_1.helpers.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface IBleConnector {
    val connectionState: StateFlow<BleConnectionState>
    val notifications: Flow<String>

    suspend fun connect(device: BleDevice)
    suspend fun disconnect()
    suspend fun readData(): String
    suspend fun sendData(data: String)
}
