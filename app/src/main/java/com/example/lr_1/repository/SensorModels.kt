package com.example.lr_1.repository

import com.example.lr_1.data.local.SensorRecord

data class SensorReading(
    val deviceId: String,
    val timestamp: Long,
    val value: Float,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val actionTriggered: Boolean = false,
    val stateLabel: String? = null,
    val confidence: Float? = null,
)

data class SensorPayload(
    val deviceId: String,
    val timestamp: Long,
    val value: Float,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
)

fun SensorReading.toPayload() = SensorPayload(deviceId, timestamp, value, type, x, y, z)

fun SensorReading.toRecord() = SensorRecord(
    deviceId = deviceId,
    timestamp = timestamp,
    sensorValue = value,
    sensorType = type,
    x = x,
    y = y,
    z = z,
    actionTriggered = actionTriggered,
    stateLabel = stateLabel,
    confidence = confidence,
    synced = false,
)

fun SensorRecord.toPayload() = SensorPayload(
    deviceId = deviceId,
    timestamp = timestamp,
    value = sensorValue,
    type = sensorType,
    x = x,
    y = y,
    z = z,
)

data class RemoteCommandPayload(
    val id: Int? = null,
    val title: String? = null,
    val completed: Boolean? = null,
    val command: String? = null,
    val openingPercent: Int? = null,
    val actuatorEnabled: Boolean? = null,
)

data class RemoteCommand(
    val openingPercent: Int? = null,
    val flashlightEnabled: Boolean? = null,
    val description: String,
)

fun RemoteCommandPayload.toDomainCommand(): RemoteCommand {
    val normalized = command?.trim()?.uppercase()
    val openingFromText = normalized
        ?.takeIf { it.startsWith("SET_OPENING|") }
        ?.substringAfter('|')
        ?.toIntOrNull()
    val opening = (openingPercent ?: openingFromText)?.coerceIn(0, 100)
        ?: when (normalized) {
            "OPEN_BLINDS" -> 100
            "CLOSE_BLINDS" -> 15
            else -> null
        }
    val flashlight = actuatorEnabled ?: when (normalized) {
        "LAMP_ON", "FLASHLIGHT_ON" -> true
        "LAMP_OFF", "FLASHLIGHT_OFF" -> false
        else -> if (opening == null) completed else null
    }
    return RemoteCommand(
        openingPercent = opening,
        flashlightEnabled = flashlight,
        description = normalized ?: title ?: "remote command #${id ?: "?"}",
    )
}
