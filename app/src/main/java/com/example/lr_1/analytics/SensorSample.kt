package com.example.lr_1.analytics

import com.example.lr_1.domain.Acceleration

data class SensorSample(
    val timestamp: Long,
    val raw: Acceleration,
    val sensorValue: Float,
    val actionTriggered: Boolean,
)
