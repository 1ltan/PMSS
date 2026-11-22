package com.example.lr_1.domain

data class ActuatorDecision(
    val openingPercent: Int,
    val isCritical: Boolean,
    val triggerHapticFeedback: Boolean,
)

class DecisionEngine(
    criticalThreshold: Float = BlindController.UPPER_THRESHOLD,
) {
    private var critical = false
    private var criticalThreshold = validatedThreshold(criticalThreshold)

    fun updateThreshold(value: Float) {
        criticalThreshold = validatedThreshold(value)
        critical = false
    }

    fun reset() {
        critical = false
    }

    fun evaluate(angle: Float, position: BlindPosition): ActuatorDecision {
        require(angle.isFinite() && angle in 0f..90f)
        val wasCritical = critical
        val releaseThreshold = (criticalThreshold - BlindController.HYSTERESIS).coerceAtLeast(0f)
        critical = if (critical) angle > releaseThreshold else angle >= criticalThreshold
        return ActuatorDecision(
            openingPercent = if (critical) 100 else position.openingPercent,
            isCritical = critical,
            triggerHapticFeedback = critical && !wasCritical,
        )
    }

    private fun validatedThreshold(value: Float): Float {
        require(value.isFinite() && value in MIN_THRESHOLD..MAX_THRESHOLD)
        return value
    }

    companion object {
        const val MIN_THRESHOLD = 5f
        const val MAX_THRESHOLD = 85f
    }
}
