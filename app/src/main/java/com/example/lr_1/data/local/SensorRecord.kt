package com.example.lr_1.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "sensor_records",
    indices = [Index(value = ["synced"])],
)
data class SensorRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "'SMART-BLIND-19'") val deviceId: String,
    val timestamp: Long,
    val sensorValue: Float,
    @ColumnInfo(defaultValue = "'incidence_angle'") val sensorType: String,
    @ColumnInfo(defaultValue = "0") val x: Float,
    @ColumnInfo(defaultValue = "0") val y: Float,
    @ColumnInfo(defaultValue = "0") val z: Float,
    val actionTriggered: Boolean,
    val stateLabel: String? = null,
    val confidence: Float? = null,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)
