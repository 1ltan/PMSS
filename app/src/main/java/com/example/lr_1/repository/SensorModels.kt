package com.example.lr_1.repository

data class SensorReading(
    val deviceId: String,
    val timestamp: Long,
    val value: Float,
    val type: String,
    val x: Float,
    val y: Float,
    val z: Float,
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
