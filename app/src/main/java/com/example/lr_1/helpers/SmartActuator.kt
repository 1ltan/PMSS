package com.example.lr_1.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.content.ContextCompat

data class ActuatorResult(
    val hardwareAvailable: Boolean,
    val applied: Boolean,
    val message: String,
)

class SmartActuator(context: Context) {
    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(CameraManager::class.java)
    private val vibratorManager = appContext.getSystemService(VibratorManager::class.java)

    fun triggerVibration(): ActuatorResult {
        val vibrator = vibratorManager.defaultVibrator
        if (!vibrator.hasVibrator()) {
            return ActuatorResult(false, false, "Вібратор недоступний; стан показано лише в UI")
        }
        return runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
        }.fold(
            onSuccess = { ActuatorResult(true, true, "Вібровідгук активовано") },
            onFailure = { ActuatorResult(true, false, it.message ?: "Не вдалося активувати вібрацію") },
        )
    }

    fun hasFlashlight(): Boolean =
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) &&
            findFlashCameraId() != null

    fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    fun setFlashlight(enabled: Boolean): ActuatorResult {
        val cameraId = findFlashCameraId()
            ?: return ActuatorResult(false, false, "Ліхтарик недоступний; команда відтворена у UI")
        return runCatching { cameraManager.setTorchMode(cameraId, enabled) }.fold(
            onSuccess = {
                ActuatorResult(true, true, if (enabled) "Ліхтарик увімкнено" else "Ліхтарик вимкнено")
            },
            onFailure = {
                ActuatorResult(true, false, it.message ?: "Не вдалося змінити стан ліхтарика")
            },
        )
    }

    private fun findFlashCameraId(): String? = runCatching {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
}
