package com.example.lr_1.monitor

import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.helpers.ble.BleConnectionState
import com.example.lr_1.helpers.ble.BleDevice

enum class SensorStatus { WAITING, ACTIVE, PAUSED, UNAVAILABLE, ERROR }
enum class CloudSyncStatus { IDLE, SYNCING, SYNCED, PENDING }
enum class RemoteCommandStatus { IDLE, POLLING, RECEIVED, ERROR }
enum class ControlSource { AUTOMATIC, MANUAL, CLOUD }

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
    val cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.IDLE,
    val lastCloudSyncAt: Long? = null,
    val remoteCommandStatus: RemoteCommandStatus = RemoteCommandStatus.IDLE,
    val lastRemoteCommand: String? = null,
    val automaticControlEnabled: Boolean = true,
    val actuatorOpeningPercent: Int? = null,
    val controlSource: ControlSource = ControlSource.AUTOMATIC,
    val isCritical: Boolean = false,
    val flashlightEnabled: Boolean = false,
    val flashlightHardwareAvailable: Boolean = false,
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
    data object SyncNow : SmartBlindAction
    data object PollRemoteCommand : SmartBlindAction
    data class SetAutomaticControl(val enabled: Boolean) : SmartBlindAction
    data class SetManualOpening(val percent: Int) : SmartBlindAction
    data object TriggerVibration : SmartBlindAction
    data class SetFlashlight(val enabled: Boolean) : SmartBlindAction
    data class CameraPermissionResult(val granted: Boolean) : SmartBlindAction
}

sealed interface SmartBlindEvent {
    data class ShowMessage(val text: String) : SmartBlindEvent
    data object RequestCameraPermission : SmartBlindEvent
}
