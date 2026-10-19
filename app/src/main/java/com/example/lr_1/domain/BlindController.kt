package com.example.lr_1.domain

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

data class Acceleration(val x: Float, val y: Float, val z: Float) {
    val isFinite get() = x.isFinite() && y.isFinite() && z.isFinite()
}

enum class BlindPosition(val label: String, val openingPercent: Int) {
    CLOSED("Прикрито", 15), HALF_OPEN("Напіввідкрито", 50), OPEN("Відкрито", 100)
}

class BlindController {
    companion object {
        const val LOWER_THRESHOLD = 30f
        const val UPPER_THRESHOLD = 60f
        const val HYSTERESIS = 3f

        fun incidenceAngle(a: Acceleration): Float? {
            if (!a.isFinite) return null
            val x = a.x.toDouble()
            val y = a.y.toDouble()
            val z = a.z.toDouble()
            val magnitude = sqrt(x * x + y * y + z * z)
            if (magnitude < 0.1) return null
            return Math.toDegrees(acos((abs(z) / magnitude).coerceIn(0.0, 1.0))).toFloat()
        }
    }

    private var previous: BlindPosition? = null

    fun reset() { previous = null }

    fun update(angle: Float): BlindPosition {
        require(angle.isFinite() && angle in 0f..90f)
        val next = when (previous) {
            null -> when {
                angle < LOWER_THRESHOLD -> BlindPosition.CLOSED
                angle < UPPER_THRESHOLD -> BlindPosition.HALF_OPEN
                else -> BlindPosition.OPEN
            }
            BlindPosition.CLOSED -> when {
                angle >= UPPER_THRESHOLD + HYSTERESIS -> BlindPosition.OPEN
                angle >= LOWER_THRESHOLD + HYSTERESIS -> BlindPosition.HALF_OPEN
                else -> BlindPosition.CLOSED
            }
            BlindPosition.HALF_OPEN -> when {
                angle <= LOWER_THRESHOLD - HYSTERESIS -> BlindPosition.CLOSED
                angle >= UPPER_THRESHOLD + HYSTERESIS -> BlindPosition.OPEN
                else -> BlindPosition.HALF_OPEN
            }
            BlindPosition.OPEN -> when {
                angle <= LOWER_THRESHOLD - HYSTERESIS -> BlindPosition.CLOSED
                angle <= UPPER_THRESHOLD - HYSTERESIS -> BlindPosition.HALF_OPEN
                else -> BlindPosition.OPEN
            }
        }
        previous = next
        return next
    }
}

class GravityFilter(private val alpha: Float = 0.15f) {
    init { require(alpha > 0f && alpha <= 1f) }
    private var previous: Acceleration? = null

    fun reset() { previous = null }

    fun update(raw: Acceleration): Acceleration? {
        if (BlindController.incidenceAngle(raw) == null) return null
        val old = previous
        fun smooth(before: Float, now: Float) =
            (before.toDouble() * (1.0 - alpha) + now.toDouble() * alpha).toFloat()
        val value = if (old == null) raw else Acceleration(
            smooth(old.x, raw.x), smooth(old.y, raw.y), smooth(old.z, raw.z)
        )
        previous = value
        return value
    }
}
