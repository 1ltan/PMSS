package com.example.lr_1

import com.example.lr_1.domain.Acceleration
import com.example.lr_1.domain.BlindController
import com.example.lr_1.domain.BlindPosition
import com.example.lr_1.domain.GravityFilter
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BlindControllerTest {
    private fun vector(degrees: Float): Acceleration {
        val angle = Math.toRadians(degrees.toDouble())
        return Acceleration((sin(angle) * 9.81).toFloat(), 0f, (cos(angle) * 9.81).toFloat())
    }

    @Test fun knownAngles() {
        listOf(0f, 30f, 45f, 60f, 90f).forEach {
            assertEquals(it, BlindController.incidenceAngle(vector(it))!!, 0.01f)
        }
    }

    @Test fun axesAndBackSideAreSymmetric() {
        assertEquals(90f, BlindController.incidenceAngle(Acceleration(0f, 9.81f, 0f))!!, 0.01f)
        assertEquals(0f, BlindController.incidenceAngle(Acceleration(0f, 0f, -9.81f))!!, 0.01f)
        assertEquals(45f, BlindController.incidenceAngle(Acceleration(3f, 0f, 3f))!!, 0.01f)
    }

    @Test fun invalidVectorsHaveNoAngle() {
        listOf(Acceleration(0f, 0f, 0f), Acceleration(0.01f, 0f, 0f),
            Acceleration(Float.NaN, 0f, 9f), Acceleration(0f, Float.POSITIVE_INFINITY, 0f))
            .forEach { assertNull(BlindController.incidenceAngle(it)) }
    }

    @Test fun initialThresholds() {
        assertEquals(BlindPosition.CLOSED, BlindController().update(29.99f))
        assertEquals(BlindPosition.HALF_OPEN, BlindController().update(30f))
        assertEquals(BlindPosition.HALF_OPEN, BlindController().update(59.99f))
        assertEquals(BlindPosition.OPEN, BlindController().update(60f))
    }

    @Test fun risingHysteresis() {
        val model = BlindController()
        model.update(0f)
        assertEquals(BlindPosition.CLOSED, model.update(32.99f))
        assertEquals(BlindPosition.HALF_OPEN, model.update(33f))
        assertEquals(BlindPosition.HALF_OPEN, model.update(62.99f))
        assertEquals(BlindPosition.OPEN, model.update(63f))
    }

    @Test fun fallingHysteresis() {
        val model = BlindController()
        model.update(90f)
        assertEquals(BlindPosition.OPEN, model.update(57.01f))
        assertEquals(BlindPosition.HALF_OPEN, model.update(57f))
        assertEquals(BlindPosition.HALF_OPEN, model.update(27.01f))
        assertEquals(BlindPosition.CLOSED, model.update(27f))
    }

    @Test fun noiseDoesNotSwitchPosition() {
        val model = BlindController()
        model.update(0f)
        listOf(29f, 30f, 31f, 30f, 29f).forEach { assertEquals(BlindPosition.CLOSED, model.update(it)) }
    }

    @Test fun jumpsAndReset() {
        val model = BlindController()
        model.update(0f)
        assertEquals(BlindPosition.OPEN, model.update(90f))
        assertEquals(BlindPosition.CLOSED, model.update(0f))
        model.reset()
        assertEquals(BlindPosition.HALF_OPEN, model.update(31f))
    }

    @Test fun filterSmoothsAndResets() {
        val filter = GravityFilter()
        assertEquals(Acceleration(0f, 0f, 10f), filter.update(Acceleration(0f, 0f, 10f)))
        val smoothed = filter.update(Acceleration(10f, 0f, 0f))!!
        assertEquals(1.5f, smoothed.x, 0.001f)
        assertEquals(8.5f, smoothed.z, 0.001f)
        filter.reset()
        assertEquals(Acceleration(10f, 0f, 0f), filter.update(Acceleration(10f, 0f, 0f)))
    }

    @Test fun invalidSampleDoesNotPoisonFilter() {
        val filter = GravityFilter()
        filter.update(Acceleration(0f, 0f, 10f))
        assertNull(filter.update(Acceleration(Float.NaN, 0f, 0f)))
        assertNull(filter.update(Acceleration(0f, 0f, 0f)))
        assertEquals(Acceleration(0f, 0f, 10f), filter.update(Acceleration(0f, 0f, 10f)))
    }

    @Test fun invalidArgumentsAreRejected() {
        listOf(-1f, 91f, Float.NaN).forEach {
            assertTrue(runCatching { BlindController().update(it) }.exceptionOrNull() is IllegalArgumentException)
        }
        listOf(0f, -0.1f, 1.1f, Float.NaN).forEach {
            assertTrue(runCatching { GravityFilter(it) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
