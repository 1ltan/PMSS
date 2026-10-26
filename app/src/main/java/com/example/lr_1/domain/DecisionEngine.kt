package com.example.lr_1.domain

data class ActuatorDecision(
    val openingPercent: Int,
    val isCritical: Boolean,
    val triggerHapticFeedback: Boolean,
)

class DecisionEngine(
    private val criticalThreshold: Float = BlindController.UPPER_THRESHOLD,
    private val releaseThreshold: Float = BlindController.UPPER_THRESHOLD - BlindController.HYSTERESIS,
) {
    private var critical = false

    fun reset() {
        critical = false
    }

    fun evaluate(angle: Float, position: BlindPosition): ActuatorDecision {
        require(angle.isFinite() && angle in 0f..90f)
        val wasCritical = critical
        critical = if (critical) angle > releaseThreshold else angle >= criticalThreshold
        return ActuatorDecision(
            openingPercent = position.openingPercent,
            isCritical = critical,
            triggerHapticFeedback = critical && !wasCritical,
        )
    }
}
