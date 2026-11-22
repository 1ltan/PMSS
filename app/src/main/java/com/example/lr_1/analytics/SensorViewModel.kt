package com.example.lr_1.analytics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lr_1.SmartBlindApplication
import com.example.lr_1.ml.BlindStateClass
import com.example.lr_1.ml.BlindStateClassifier
import com.example.lr_1.ml.SensorWindowBuffer
import com.example.lr_1.repository.SensorReading
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SensorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SmartBlindApplication
    private val repository = app.sensorRepository
    private val buffer = SensorWindowBuffer()
    private val classifier = runCatching { BlindStateClassifier(application) }.getOrNull()

    private val _state = MutableStateFlow(
        SensorAnalyticsState(
            modelReady = classifier != null,
            modelError = if (classifier == null) "Не вдалося завантажити TFLite-модель" else null,
        )
    )
    val state: StateFlow<SensorAnalyticsState> = _state.asStateFlow()

    private val _events = Channel<AnalyticsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var inferenceJob: Job? = null
    private var receivedSamples = 0
    private var lastSavedAt = 0L
    private var lastSavedValue: Float? = null
    private var lastModelState: BlindStateClass? = null

    init {
        viewModelScope.launch {
            repository.observeLatest(MAX_CHART_POINTS)
                .catch { error ->
                    _state.update { it.copy(databaseError = error.message ?: "Помилка читання Room") }
                }
                .collect { records -> _state.update { it.copy(records = records, databaseError = null) } }
        }
    }

    fun onAction(action: AnalyticsAction) {
        when (action) {
            AnalyticsAction.ClearHistory -> clearHistory()
        }
    }

    fun onSensorSample(sample: SensorSample) {
        buffer.add(sample.raw)
        receivedSamples++
        _state.update { it.copy(windowSamples = buffer.snapshot().size) }

        val interval = if (_state.value.prediction?.state == BlindStateClass.RAPID_CHANGE) {
            RUNNING_RECORD_INTERVAL_MS
        } else {
            SensorAnalyticsState.NORMAL_RECORD_INTERVAL_MS
        }
        val valueChanged = lastSavedValue?.let { abs(sample.sensorValue - it) >= SIGNIFICANT_CHANGE } ?: true
        if (sample.actionTriggered || valueChanged || sample.timestamp - lastSavedAt >= interval) {
            lastSavedAt = sample.timestamp
            lastSavedValue = sample.sensorValue
            val prediction = _state.value.prediction
            viewModelScope.launch {
                runCatching {
                    repository.enqueue(
                        SensorReading(
                            deviceId = DEVICE_ID,
                            timestamp = sample.timestamp,
                            value = sample.sensorValue,
                            type = SENSOR_TYPE,
                            x = sample.raw.x,
                            y = sample.raw.y,
                            z = sample.raw.z,
                            actionTriggered = sample.actionTriggered,
                            stateLabel = prediction?.state?.label,
                            confidence = prediction?.confidence,
                        )
                    )
                }.onFailure { error ->
                    _state.update { it.copy(databaseError = error.message ?: "Помилка запису Room") }
                }
            }
        }

        if (buffer.isFull && receivedSamples % INFERENCE_STRIDE == 0 && inferenceJob?.isActive != true) {
            classify(buffer.snapshot())
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            runCatching { repository.clearHistory() }
                .onSuccess { _events.send(AnalyticsEvent.ShowMessage("Локальну історію очищено")) }
                .onFailure { error ->
                    val message = error.message ?: "Не вдалося очистити історію"
                    _state.update { it.copy(databaseError = message) }
                    _events.send(AnalyticsEvent.ShowMessage(message))
                }
        }
    }

    private fun classify(window: List<com.example.lr_1.domain.Acceleration>) {
        val localClassifier = classifier ?: return
        inferenceJob = viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) { localClassifier.classify(window) }
            }
            result.onSuccess { prediction ->
                val isNewAnomaly = prediction.state == BlindStateClass.ANOMALY &&
                    lastModelState != BlindStateClass.ANOMALY
                if (isNewAnomaly) app.smartActuator.triggerVibration()
                lastModelState = prediction.state
                _state.update {
                    it.copy(
                        prediction = prediction,
                        modelError = null,
                        recordingIntervalMs = if (prediction.state == BlindStateClass.RAPID_CHANGE) {
                            RUNNING_RECORD_INTERVAL_MS
                        } else {
                            SensorAnalyticsState.NORMAL_RECORD_INTERVAL_MS
                        },
                        anomalyActuated = isNewAnomaly,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(modelError = error.message ?: "Помилка TensorFlow Lite inference") }
            }
        }
    }

    override fun onCleared() {
        classifier?.close()
        super.onCleared()
    }

    companion object {
        private const val MAX_CHART_POINTS = 50
        private const val SIGNIFICANT_CHANGE = 1f
        private const val RUNNING_RECORD_INTERVAL_MS = 250L
        private const val INFERENCE_STRIDE = 5
        private const val DEVICE_ID = "SMART-BLIND-19"
        private const val SENSOR_TYPE = "incidence_angle"
    }
}
