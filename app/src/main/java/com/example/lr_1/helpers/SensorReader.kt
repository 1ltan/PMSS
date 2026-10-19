package com.example.lr_1.helpers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.lr_1.domain.Acceleration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Перетворює Android SensorManager на холодний Flow та звільняє listener при cancel(). */
class SensorReader(context: Context) {
    private val manager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun readings(): Flow<Acceleration> = callbackFlow {
        val sensor = accelerometer
        if (sensor == null) {
            close(SensorUnavailableException())
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
                    trySend(Acceleration(event.values[0], event.values[1], event.values[2]))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                Log.d(LOG_TAG, "Sensor accuracy: $accuracy")
            }
        }

        val registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        if (!registered) {
            close(SensorRegistrationException())
            return@callbackFlow
        }
        Log.d(LOG_TAG, "registerListener: true")
        awaitClose {
            manager.unregisterListener(listener)
            Log.d(LOG_TAG, "unregisterListener: зчитування зупинено")
        }
    }

    companion object {
        private const val LOG_TAG = "SmartBlinds"
    }
}

class SensorUnavailableException : IllegalStateException("Акселерометр відсутній")
class SensorRegistrationException : IllegalStateException("Не вдалося зареєструвати сенсор")
