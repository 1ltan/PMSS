package com.example.lr_1.helpers.ble

import java.util.UUID

data class BleDevice(
    val id: String,
    val name: String,
    val signalStrengthDbm: Int,
)

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/** Навчальний GATT-профіль Smart Blind Controller. */
object SmartBlindGattProfile {
    val SERVICE_UUID: UUID = UUID.fromString("19b10000-e8f2-537e-4f6c-d104768a1214")
    val STATUS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("19b10001-e8f2-537e-4f6c-d104768a1214")
    val COMMAND_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("19b10002-e8f2-537e-4f6c-d104768a1214")
}
