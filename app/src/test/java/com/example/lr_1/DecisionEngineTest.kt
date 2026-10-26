package com.example.lr_1

import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.domain.DecisionEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    @Test fun criticalTransitionTriggersHapticOnlyOnce() {
        val engine = DecisionEngine()
        assertFalse(engine.evaluate(59f, BlindPosition.HALF_OPEN).triggerHapticFeedback)
        assertTrue(engine.evaluate(60f, BlindPosition.OPEN).triggerHapticFeedback)
        assertFalse(engine.evaluate(70f, BlindPosition.OPEN).triggerHapticFeedback)
    }

    @Test fun hysteresisPreventsRepeatedCriticalTransitions() {
        val engine = DecisionEngine()
        engine.evaluate(60f, BlindPosition.OPEN)
        assertTrue(engine.evaluate(58f, BlindPosition.HALF_OPEN).isCritical)
        assertFalse(engine.evaluate(57f, BlindPosition.HALF_OPEN).isCritical)
        assertTrue(engine.evaluate(61f, BlindPosition.OPEN).triggerHapticFeedback)
    }
}
