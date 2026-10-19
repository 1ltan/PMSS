package com.example.lr_1.helpers.ble

import kotlinx.coroutines.delay

/** Імітує BLE scan: віртуальний контролер з'являється рівно через 2 секунди. */
class MockBleScanner {
    suspend fun scan(): List<BleDevice> {
        delay(SCAN_DELAY_MS)
        return listOf(
            BleDevice(
                id = "MOCK-SBC-19",
                name = "Smart Blind Hub #19",
                signalStrengthDbm = -47,
            )
        )
    }

    companion object {
        const val SCAN_DELAY_MS = 2_000L
    }
}
