package com.example.lr_1.monitor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindController
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SmartBlindViewModel(application: Application) : AndroidViewModel(application) {
    private val sensorReader = SensorReader(application)
    private val scanner = MockBleScanner()
    private val connector: IBleConnector = MockBleConnector()
    private val filter = GravityFilter()
    private val controller = BlindController()

    private val _state = MutableStateFlow(SmartBlindState())
    val state: StateFlow<SmartBlindState> = _state.asStateFlow()

    private val _events = Channel<SmartBlindEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var sensorJob: Job? = null
    private var scanJob: Job? = null

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
        }
    }

    private fun startSensor() {
        if (sensorJob != null) return
        filter.reset()
        controller.reset()
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
    }

    private fun stopSensor() {
        sensorJob?.cancel()
        sensorJob = null
        _state.update { it.copy(sensorStatus = SensorStatus.PAUSED) }
    }

    private fun handleReading(raw: Acceleration) {
        val angle = filter.update(raw)?.let(BlindController::incidenceAngle)
        val position = angle?.let(controller::update)
        val oldPosition = _state.value.position
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
            )
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
        private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
