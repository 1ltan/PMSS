package com.example.lr_1

import com.example.lr_1.domain.Acceleration
import com.example.lr_1.ml.BlindStateClassifier
import com.example.lr_1.ml.SensorWindowBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorWindowBufferTest {
    @Test
    fun `buffer keeps the newest values in chronological order`() {
        val buffer = SensorWindowBuffer(capacity = 3)

        (1..4).forEach { buffer.add(Acceleration(it.toFloat(), 0f, 9.8f)) }

        assertTrue(buffer.isFull)
        assertEquals(listOf(2f, 3f, 4f), buffer.snapshot().map { it.x })
    }

    @Test
    fun `clear resets buffer`() {
        val buffer = SensorWindowBuffer(capacity = 2)
        buffer.add(Acceleration(0f, 0f, 9.8f))
        buffer.add(Acceleration(0f, 0f, 9.8f))

        buffer.clear()

        assertFalse(buffer.isFull)
        assertTrue(buffer.snapshot().isEmpty())
    }

    @Test
    fun `stationary gravity produces near zero features`() {
        val window = List(SensorWindowBuffer.WINDOW_SIZE) { Acceleration(0f, 0f, 9.80665f) }

        val features = BlindStateClassifier.preprocess(window)

        assertEquals(BlindStateClassifier.FEATURE_COUNT, features.size)
        assertArrayEquals(FloatArray(BlindStateClassifier.FEATURE_COUNT), features, 1e-5f)
    }

    @Test
    fun `orientation change is preserved as motion`() {
        val window = List(SensorWindowBuffer.WINDOW_SIZE) { index ->
            if (index % 2 == 0) Acceleration(0f, 0f, 9.80665f)
            else Acceleration(9.80665f, 0f, 0f)
        }

        val features = BlindStateClassifier.preprocess(window)

        assertTrue(features.drop(SensorWindowBuffer.WINDOW_SIZE).any { it > 1f })
    }
}
