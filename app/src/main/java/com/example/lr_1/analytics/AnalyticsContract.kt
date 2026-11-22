package com.example.lr_1.analytics

import com.example.lr_1.data.local.SensorRecord
import com.example.lr_1.ml.BlindStatePrediction

data class SensorAnalyticsState(
    val records: List<SensorRecord> = emptyList(),
    val prediction: BlindStatePrediction? = null,
    val modelReady: Boolean = false,
    val modelError: String? = null,
    val databaseError: String? = null,
    val windowSamples: Int = 0,
    val recordingIntervalMs: Long = NORMAL_RECORD_INTERVAL_MS,
    val anomalyActuated: Boolean = false,
) {
    companion object {
        const val NORMAL_RECORD_INTERVAL_MS = 1_000L
    }
}

sealed interface AnalyticsAction {
    data object ClearHistory : AnalyticsAction
}

sealed interface AnalyticsEvent {
    data class ShowMessage(val text: String) : AnalyticsEvent
}
