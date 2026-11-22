package com.example.lr_1.ml

import com.example.lr_1.domain.Acceleration

class SensorWindowBuffer(private val capacity: Int = WINDOW_SIZE) {
    private val values = arrayOfNulls<Acceleration>(capacity)
    private var nextIndex = 0
    private var size = 0

    init {
        require(capacity > 1)
    }

    val isFull: Boolean get() = size == capacity

    fun add(value: Acceleration) {
        values[nextIndex] = value
        nextIndex = (nextIndex + 1) % capacity
        if (size < capacity) size++
    }

    fun snapshot(): List<Acceleration> {
        val start = if (isFull) nextIndex else 0
        return List(size) { index ->
            values[(start + index) % capacity] ?: error("Incomplete sensor buffer")
        }
    }

    fun clear() {
        values.fill(null)
        nextIndex = 0
        size = 0
    }

    companion object {
        const val WINDOW_SIZE = 20
    }
}
