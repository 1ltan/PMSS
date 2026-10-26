package com.example.lr_1

import android.app.Application
import com.example.lr_1.helpers.NetworkMonitor
import com.example.lr_1.helpers.SmartActuator
import com.example.lr_1.repository.CloudServiceFactory
import com.example.lr_1.repository.SensorRepository

class SmartBlindApplication : Application() {
    val sensorRepository: SensorRepository by lazy {
        SensorRepository(
            api = CloudServiceFactory.create(BuildConfig.CLOUD_BASE_URL),
            networkMonitor = NetworkMonitor(this),
            uploadEndpoint = BuildConfig.CLOUD_UPLOAD_PATH,
            commandEndpoint = BuildConfig.CLOUD_COMMAND_PATH,
        )
    }

    val smartActuator: SmartActuator by lazy { SmartActuator(this) }
}
