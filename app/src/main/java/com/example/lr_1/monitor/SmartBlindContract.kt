package com.example.lr_1.monitor

import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.helpers.ble.BleConnectionState
import com.example.lr_1.helpers.ble.BleDevice

enum class SensorStatus { WAITING, ACTIVE, PAUSED, UNAVAILABLE, ERROR }

data class SmartBlindState(
    val raw: Acceleration? = null,
    val angle: Float? = null,
    val position: BlindPosition? = null,
    val sensorStatus: SensorStatus = SensorStatus.WAITING,
    val samples: Long = 0,
    val invalidReading: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<BleDevice> = emptyList(),
    val selectedDevice: BleDevice? = null,
    val connectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val terminalLines: List<String> = emptyList(),
)

sealed interface SmartBlindAction {
    data object StartSensor : SmartBlindAction
    data object StopSensor : SmartBlindAction
    data object SearchDevices : SmartBlindAction
    data class SelectDevice(val device: BleDevice) : SmartBlindAction
    data object ConnectSelected : SmartBlindAction
    data object Disconnect : SmartBlindAction
    data object SendCurrentPosition : SmartBlindAction
    data object ReadRemoteStatus : SmartBlindAction
    data object ClearTerminal : SmartBlindAction
}

sealed interface SmartBlindEvent {
    data class ShowMessage(val text: String) : SmartBlindEvent
}
