package com.example.lr_1

import com.example.lr_1.repository.SensorReading
import com.example.lr_1.repository.toPayload
import com.example.lr_1.repository.toRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SensorModelsTest {
    private val reading = SensorReading(
        deviceId = "SMART-BLIND-19",
        timestamp = 1234L,
        value = 62.5f,
        type = "incidence_angle",
        x = 1f,
        y = 2f,
        z = 3f,
        actionTriggered = true,
        stateLabel = "Аномалія сенсора",
        confidence = 0.91f,
    )

    @Test fun queuedRecordPreservesDomainDataAndStartsUnsynced() {
        val record = reading.toRecord()

        assertEquals(reading.deviceId, record.deviceId)
        assertEquals(reading.value, record.sensorValue)
        assertEquals(reading.stateLabel, record.stateLabel)
        assertEquals(reading.confidence, record.confidence)
        assertFalse(record.synced)
    }

    @Test fun cloudPayloadContainsOnlyTransportFields() {
        val payload = reading.toPayload()

        assertEquals(reading.deviceId, payload.deviceId)
        assertEquals(reading.timestamp, payload.timestamp)
        assertEquals(reading.value, payload.value)
        assertEquals(reading.type, payload.type)
    }
}
