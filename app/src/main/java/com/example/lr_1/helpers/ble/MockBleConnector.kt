package com.example.lr_1.helpers.ble

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mock GATT-клієнт: CONNECT, WRITE команди та NOTIFY підтвердження без реального Bluetooth. */
class MockBleConnector : IBleConnector {
    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    override val connectionState = _connectionState.asStateFlow()

    private val _notifications = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val notifications: Flow<String> = _notifications.asSharedFlow()

    private var connectedDevice: BleDevice? = null
    private var lastOpeningPercent = 0

    override suspend fun connect(device: BleDevice) {
        if (_connectionState.value != BleConnectionState.DISCONNECTED) return
        _connectionState.value = BleConnectionState.CONNECTING
        Log.d(LOG_TAG, "CONNECT ${device.name} (${device.id})")
        delay(CONNECT_DELAY_MS)
        if (_connectionState.value != BleConnectionState.CONNECTING) return
        connectedDevice = device
        _connectionState.value = BleConnectionState.CONNECTED
        _notifications.emit("STATUS|READY|device=${device.id}")
        Log.d(LOG_TAG, "CONNECTED ${device.name}")
    }

    override suspend fun disconnect() {
        val device = connectedDevice
        connectedDevice = null
        _connectionState.value = BleConnectionState.DISCONNECTED
        if (device != null) Log.d(LOG_TAG, "DISCONNECTED ${device.name}")
    }

    override suspend fun sendData(data: String) {
        requireConnected()
        require(data.isNotBlank()) { "Порожній пакет не підтримується" }
        Log.d(LOG_TAG, "WRITE ${SmartBlindGattProfile.COMMAND_CHARACTERISTIC_UUID}: $data")
        delay(WRITE_DELAY_MS)
        data.removePrefix("SET_OPENING|").toIntOrNull()?.let { percent ->
            if (percent in 0..100) lastOpeningPercent = percent
        }
        val notification = "ACK|$data"
        _notifications.emit(notification)
        Log.d(LOG_TAG, "NOTIFY ${SmartBlindGattProfile.STATUS_CHARACTERISTIC_UUID}: $notification")
    }

    override suspend fun readData(): String {
        val device = requireConnected()
        delay(READ_DELAY_MS)
        val packet = "STATUS|OPENING|$lastOpeningPercent|device=${device.id}"
        Log.d(LOG_TAG, "READ ${SmartBlindGattProfile.STATUS_CHARACTERISTIC_UUID}: $packet")
        return packet
    }

    private fun requireConnected(): BleDevice {
        check(_connectionState.value == BleConnectionState.CONNECTED) {
            "Немає активного BLE-з'єднання"
        }
        return checkNotNull(connectedDevice) { "Підключений пристрій не визначений" }
    }

    companion object {
        private const val LOG_TAG = "SmartBlindsBLE"
        private const val CONNECT_DELAY_MS = 650L
        private const val READ_DELAY_MS = 180L
        private const val WRITE_DELAY_MS = 250L
    }
}
