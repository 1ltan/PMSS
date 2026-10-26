package com.example.lr_1.monitor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lr_1.SmartBlindApplication
import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindController
import com.example.lr_1.domain.DecisionEngine
import com.example.lr_1.domain.GravityFilter
import com.example.lr_1.helpers.SensorRegistrationException
import com.example.lr_1.helpers.SensorReader
import com.example.lr_1.helpers.SensorUnavailableException
import com.example.lr_1.helpers.ble.BleConnectionState
import com.example.lr_1.helpers.ble.BleDevice
import com.example.lr_1.helpers.ble.IBleConnector
import com.example.lr_1.helpers.ble.MockBleConnector
import com.example.lr_1.helpers.ble.MockBleScanner
import com.example.lr_1.protocol.BlindCommand
import com.example.lr_1.repository.RemoteCommand
import com.example.lr_1.repository.SensorReading
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class SmartBlindViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SmartBlindApplication
    private val sensorReader = SensorReader(application)
    private val repository = app.sensorRepository
    private val actuator = app.smartActuator
    private val scanner = MockBleScanner()
    private val connector: IBleConnector = MockBleConnector()
    private val filter = GravityFilter()
    private val controller = BlindController()
    private val decisionEngine = DecisionEngine()

    private val _state = MutableStateFlow(
        SmartBlindState(flashlightHardwareAvailable = actuator.hasFlashlight())
    )
    val state: StateFlow<SmartBlindState> = _state.asStateFlow()

    private val _events = Channel<SmartBlindEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var sensorJob: Job? = null
    private var scanJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var commandPollingJob: Job? = null
    private var uploadJob: Job? = null
    private var commandRequestJob: Job? = null
    private var pendingReading: SensorReading? = null
    private var pendingFlashlightSource: ControlSource? = null

    init {
        observeBleState()
        observeNotifications()
    }

    /** Єдиний вхід у ViewModel: UI та Activity надсилають лише Action. */
    fun onAction(action: SmartBlindAction) {
        when (action) {
            SmartBlindAction.StartSensor -> startSensor()
            SmartBlindAction.StopSensor -> stopSensor()
            SmartBlindAction.SearchDevices -> searchDevices()
            is SmartBlindAction.SelectDevice -> selectDevice(action.device)
            SmartBlindAction.ConnectSelected -> connectSelected()
            SmartBlindAction.Disconnect -> disconnect()
            SmartBlindAction.SendCurrentPosition -> sendCurrentPosition()
            SmartBlindAction.ReadRemoteStatus -> readRemoteStatus()
            SmartBlindAction.ClearTerminal -> _state.update { it.copy(terminalLines = emptyList()) }
            SmartBlindAction.SyncNow -> uploadLatestReading()
            SmartBlindAction.PollRemoteCommand -> pollRemoteCommand()
            is SmartBlindAction.SetAutomaticControl -> setAutomaticControl(action.enabled)
            is SmartBlindAction.SetManualOpening -> setManualOpening(action.percent)
            SmartBlindAction.TriggerVibration -> triggerVibration()
            is SmartBlindAction.SetFlashlight -> setFlashlight(action.enabled, ControlSource.MANUAL)
            is SmartBlindAction.CameraPermissionResult -> handleCameraPermission(action.granted)
        }
    }

    private fun startSensor() {
        if (sensorJob != null) return
        filter.reset()
        controller.reset()
        decisionEngine.reset()
        _state.update {
            it.copy(
                raw = null,
                angle = null,
                position = null,
                sensorStatus = SensorStatus.WAITING,
                invalidReading = false,
            )
        }
        sensorJob = viewModelScope.launch {
            sensorReader.readings()
                .catch { error ->
                    when (error) {
                        is SensorUnavailableException ->
                            _state.update { it.copy(sensorStatus = SensorStatus.UNAVAILABLE) }
                        is SensorRegistrationException ->
                            _state.update { it.copy(sensorStatus = SensorStatus.ERROR) }
                        is CancellationException -> throw error
                        else -> {
                            Log.e(LOG_TAG, "Sensor stream failed", error)
                            _state.update { it.copy(sensorStatus = SensorStatus.ERROR) }
                        }
                    }
                }
                .onCompletion { sensorJob = null }
                .collect(::handleReading)
        }
        startCloudLoops()
    }

    private fun stopSensor() {
        sensorJob?.cancel()
        sensorJob = null
        cloudSyncJob?.cancel()
        cloudSyncJob = null
        commandPollingJob?.cancel()
        commandPollingJob = null
        uploadJob?.cancel()
        uploadJob = null
        commandRequestJob?.cancel()
        commandRequestJob = null
        _state.update { it.copy(sensorStatus = SensorStatus.PAUSED) }
    }

    private fun handleReading(raw: Acceleration) {
        val angle = filter.update(raw)?.let(BlindController::incidenceAngle)
        val position = angle?.let(controller::update)
        val oldPosition = _state.value.position
        val decision = if (angle != null && position != null) {
            decisionEngine.evaluate(angle, position)
        } else {
            null
        }
        if (position != null && position != oldPosition) {
            Log.d(LOG_TAG, "angle=$angle position=$position opening=${position.openingPercent}%")
        }
        _state.update {
            it.copy(
                raw = raw,
                angle = angle,
                position = position ?: it.position,
                sensorStatus = SensorStatus.ACTIVE,
                samples = it.samples + 1,
                invalidReading = angle == null,
                isCritical = decision?.isCritical ?: it.isCritical,
                actuatorOpeningPercent = if (it.automaticControlEnabled) {
                    decision?.openingPercent ?: it.actuatorOpeningPercent
                } else {
                    it.actuatorOpeningPercent
                },
                controlSource = if (it.automaticControlEnabled && decision != null) {
                    ControlSource.AUTOMATIC
                } else {
                    it.controlSource
                },
            )
        }
        if (_state.value.automaticControlEnabled && decision?.triggerHapticFeedback == true) {
            val result = actuator.triggerVibration()
            addLog("ACTUATOR AUTO_VIBRATION: ${result.message}")
            emitMessage(result.message)
        }
    }

    private fun startCloudLoops() {
        if (cloudSyncJob == null) {
            cloudSyncJob = viewModelScope.launch {
                while (currentCoroutineContext().isActive) {
                    delay(CLOUD_SYNC_INTERVAL_MS)
                    uploadLatestReading()
                }
            }
        }
        if (commandPollingJob == null) {
            commandPollingJob = viewModelScope.launch {
                pollRemoteCommand(showFailure = false)
                while (currentCoroutineContext().isActive) {
                    delay(COMMAND_POLL_INTERVAL_MS)
                    pollRemoteCommand(showFailure = false)
                }
            }
        }
    }

    private fun currentReading(): SensorReading? {
        val state = _state.value
        val raw = state.raw ?: return null
        val angle = state.angle ?: return null
        return SensorReading(
            deviceId = DEVICE_ID,
            timestamp = System.currentTimeMillis(),
            value = angle,
            type = SENSOR_TYPE,
            x = raw.x,
            y = raw.y,
            z = raw.z,
        )
    }

    private fun uploadLatestReading() {
        if (uploadJob != null) return
        val reading = pendingReading ?: currentReading()
        if (reading == null) {
            emitMessage("Немає валідних сенсорних даних для синхронізації")
            return
        }
        uploadJob = viewModelScope.launch {
            val wasPending = _state.value.cloudSyncStatus == CloudSyncStatus.PENDING
            try {
                _state.update { it.copy(cloudSyncStatus = CloudSyncStatus.SYNCING) }
                repository.save(reading)
                    .onSuccess {
                        pendingReading = null
                        _state.update {
                            it.copy(
                                cloudSyncStatus = CloudSyncStatus.SYNCED,
                                lastCloudSyncAt = System.currentTimeMillis(),
                            )
                        }
                        addLog("CLOUD POST angle=${reading.value}")
                    }
                    .onFailure { error ->
                        pendingReading = reading
                        _state.update { it.copy(cloudSyncStatus = CloudSyncStatus.PENDING) }
                        addLog("CLOUD PENDING: ${error.message}")
                        if (!wasPending) emitMessage("Хмарна синхронізація очікує мережу")
                    }
            } finally {
                uploadJob = null
            }
        }
    }

    private fun pollRemoteCommand(showFailure: Boolean = true) {
        if (commandRequestJob != null) return
        commandRequestJob = viewModelScope.launch {
            try {
                _state.update { it.copy(remoteCommandStatus = RemoteCommandStatus.POLLING) }
                repository.readRemoteCommand()
                    .onSuccess(::applyRemoteCommand)
                    .onFailure { error ->
                        _state.update { it.copy(remoteCommandStatus = RemoteCommandStatus.ERROR) }
                        addLog("CLOUD COMMAND ERROR: ${error.message}")
                        if (showFailure) emitMessage(error.message ?: "Не вдалося отримати хмарну команду")
                    }
            } finally {
                commandRequestJob = null
            }
        }
    }

    private fun applyRemoteCommand(command: RemoteCommand) {
        _state.update {
            it.copy(
                remoteCommandStatus = RemoteCommandStatus.RECEIVED,
                lastRemoteCommand = command.description,
            )
        }
        command.openingPercent?.let { percent ->
            _state.update {
                it.copy(
                    automaticControlEnabled = false,
                    actuatorOpeningPercent = percent,
                    controlSource = ControlSource.CLOUD,
                )
            }
            if (_state.value.connectionState == BleConnectionState.CONNECTED) {
                send(BlindCommand.SetOpening(percent))
            }
        }
        command.flashlightEnabled?.let { setFlashlight(it, ControlSource.CLOUD) }
        addLog("CLOUD COMMAND ${command.description}")
    }

    private fun setAutomaticControl(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                automaticControlEnabled = enabled,
                actuatorOpeningPercent = if (enabled) {
                    current.position?.openingPercent ?: current.actuatorOpeningPercent
                } else {
                    current.actuatorOpeningPercent
                },
                controlSource = if (enabled) ControlSource.AUTOMATIC else current.controlSource,
            )
        }
    }

    private fun setManualOpening(percent: Int) {
        require(percent in 0..100)
        _state.update {
            it.copy(
                automaticControlEnabled = false,
                actuatorOpeningPercent = percent,
                controlSource = ControlSource.MANUAL,
            )
        }
        if (_state.value.connectionState == BleConnectionState.CONNECTED) {
            send(BlindCommand.SetOpening(percent))
        }
        addLog("ACTUATOR MANUAL_OPENING $percent")
    }

    private fun triggerVibration() {
        val result = actuator.triggerVibration()
        addLog("ACTUATOR VIBRATION: ${result.message}")
        emitMessage(result.message)
    }

    private fun setFlashlight(enabled: Boolean, source: ControlSource) {
        if (enabled && actuator.hasFlashlight() && !actuator.hasCameraPermission()) {
            pendingFlashlightSource = source
            viewModelScope.launch { _events.send(SmartBlindEvent.RequestCameraPermission) }
            return
        }
        if (!enabled && actuator.hasFlashlight() && !actuator.hasCameraPermission()) {
            _state.update { it.copy(flashlightEnabled = false, controlSource = source) }
            addLog("ACTUATOR FLASHLIGHT=false: hardware was not controlled without CAMERA permission")
            return
        }
        val result = actuator.setFlashlight(enabled)
        _state.update {
            it.copy(
                flashlightEnabled = enabled,
                flashlightHardwareAvailable = result.hardwareAvailable,
                controlSource = source,
            )
        }
        addLog("ACTUATOR FLASHLIGHT=$enabled: ${result.message}")
        if (!result.applied && source == ControlSource.MANUAL) emitMessage(result.message)
    }

    private fun handleCameraPermission(granted: Boolean) {
        val source = pendingFlashlightSource ?: ControlSource.MANUAL
        pendingFlashlightSource = null
        if (granted) {
            setFlashlight(true, source)
        } else {
            emitMessage("Без дозволу CAMERA апаратний ліхтарик недоступний")
        }
    }

    private fun searchDevices() {
        if (scanJob != null) return
        scanJob = viewModelScope.launch {
            _state.update { it.copy(isScanning = true, devices = emptyList(), selectedDevice = null) }
            addLog("SCAN почато; очікування 2 с")
            try {
                val found = scanner.scan()
                _state.update { it.copy(devices = found, selectedDevice = found.firstOrNull()) }
                addLog("SCAN знайдено ${found.size}: ${found.joinToString { it.name }}")
                _events.send(SmartBlindEvent.ShowMessage("Віртуальний BLE-пристрій знайдено"))
            } finally {
                _state.update { it.copy(isScanning = false) }
                scanJob = null
            }
        }
    }

    private fun selectDevice(device: BleDevice) {
        _state.update { it.copy(selectedDevice = device) }
        addLog("SELECT ${device.name} (${device.signalStrengthDbm} dBm)")
    }

    private fun connectSelected() {
        val device = _state.value.selectedDevice
        if (device == null) {
            emitMessage("Спочатку знайдіть та оберіть пристрій")
            return
        }
        viewModelScope.launch {
            runCatching { connector.connect(device) }
                .onFailure { handleBleFailure("CONNECT", it) }
        }
    }

    private fun disconnect() {
        viewModelScope.launch {
            runCatching { connector.disconnect() }
                .onFailure { handleBleFailure("DISCONNECT", it) }
        }
    }

    private fun sendCurrentPosition() {
        val percent = _state.value.position?.openingPercent
        if (percent == null) {
            emitMessage("Дочекайтеся валідного значення сенсора")
            return
        }
        send(BlindCommand.SetOpening(percent))
    }

    private fun send(command: BlindCommand) {
        viewModelScope.launch {
            val packet = command.encode()
            addLog("WRITE $packet")
            runCatching { connector.sendData(packet) }
                .onFailure { handleBleFailure("WRITE", it) }
        }
    }

    private fun readRemoteStatus() {
        viewModelScope.launch {
            addLog("READ ${BlindCommand.ReadStatus.encode()}")
            runCatching { connector.readData() }
                .onSuccess { packet -> addLog("READ_RESULT $packet") }
                .onFailure { handleBleFailure("READ", it) }
        }
    }

    private fun observeBleState() = viewModelScope.launch {
        connector.connectionState.collect { connection ->
            val previous = _state.value.connectionState
            _state.update { it.copy(connectionState = connection) }
            if (connection != previous) addLog("STATE $connection")
        }
    }

    private fun observeNotifications() = viewModelScope.launch {
        connector.notifications.collect { packet -> addLog("NOTIFY $packet") }
    }

    private fun handleBleFailure(operation: String, error: Throwable) {
        if (error is CancellationException) throw error
        addLog("ERROR $operation: ${error.message ?: error::class.java.simpleName}")
        emitMessage(error.message ?: "Помилка BLE-операції")
    }

    private fun emitMessage(text: String) {
        viewModelScope.launch { _events.send(SmartBlindEvent.ShowMessage(text)) }
    }

    private fun addLog(message: String) {
        val line = "${LocalTime.now().format(TIME_FORMAT)}  $message"
        Log.d(BLE_LOG_TAG, line)
        _state.update { current ->
            current.copy(terminalLines = (current.terminalLines + line).takeLast(MAX_LOG_LINES))
        }
    }

    companion object {
        private const val LOG_TAG = "SmartBlinds"
        private const val BLE_LOG_TAG = "SmartBlindsBLE"
        private const val MAX_LOG_LINES = 40
        private const val DEVICE_ID = "SMART-BLIND-19"
        private const val SENSOR_TYPE = "incidence_angle"
        private const val CLOUD_SYNC_INTERVAL_MS = 5_000L
        private const val COMMAND_POLL_INTERVAL_MS = 10_000L
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
