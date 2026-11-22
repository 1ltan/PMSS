package com.example.lr_1.ml

import android.content.Context
import com.example.lr_1.domain.Acceleration
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.sqrt
import org.tensorflow.lite.Interpreter

enum class BlindStateClass(val label: String) {
    STABLE("Стабільний стан"),
    GRADUAL_CHANGE("Плавна зміна"),
    RAPID_CHANGE("Швидка зміна"),
    ANOMALY("Аномалія сенсора"),
}

data class BlindStatePrediction(
    val state: BlindStateClass,
    val confidence: Float,
    val probabilities: List<Float>,
)

class BlindStateClassifier(context: Context) : AutoCloseable {
    private val interpreter = Interpreter(loadModelFile(context), Interpreter.Options().setNumThreads(2))

    init {
        val inputShape = interpreter.getInputTensor(0).shape()
        val outputShape = interpreter.getOutputTensor(0).shape()
        require(inputShape.contentEquals(intArrayOf(1, FEATURE_COUNT))) {
            "Expected model input [1, $FEATURE_COUNT], got ${inputShape.contentToString()}"
        }
        require(outputShape.contentEquals(intArrayOf(1, BlindStateClass.entries.size))) {
            "Expected model output [1, ${BlindStateClass.entries.size}], got ${outputShape.contentToString()}"
        }
    }

    fun classify(window: List<Acceleration>): BlindStatePrediction {
        val features = preprocess(window)
        val output = Array(1) { FloatArray(BlindStateClass.entries.size) }
        interpreter.run(arrayOf(features), output)
        val probabilities = output[0]
        val bestIndex = probabilities.indices.maxBy(probabilities::get)
        return BlindStatePrediction(
            state = BlindStateClass.entries[bestIndex],
            confidence = probabilities[bestIndex].coerceIn(0f, 1f),
            probabilities = probabilities.toList(),
        )
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_FILE = "activity_classifier.tflite"
        const val FEATURE_COUNT = SensorWindowBuffer.WINDOW_SIZE * 2
        private const val STANDARD_GRAVITY = 9.80665f

        fun preprocess(window: List<Acceleration>): FloatArray {
            require(window.size == SensorWindowBuffer.WINDOW_SIZE)
            val magnitudes = window.map { sample ->
                sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
            }
            return FloatArray(FEATURE_COUNT).also { features ->
                magnitudes.forEachIndexed { index, magnitude ->
                    features[index] = (abs(magnitude - STANDARD_GRAVITY) / STANDARD_GRAVITY)
                        .coerceIn(0f, 3f)
                    val sample = window[index]
                    val previous = window.getOrElse(index - 1) { sample }
                    val dx = sample.x - previous.x
                    val dy = sample.y - previous.y
                    val dz = sample.z - previous.z
                    features[SensorWindowBuffer.WINDOW_SIZE + index] =
                        (sqrt(dx * dx + dy * dy + dz * dz) / STANDARD_GRAVITY).coerceIn(0f, 3f)
                }
            }
        }

        private fun loadModelFile(context: Context): MappedByteBuffer {
            val descriptor = context.assets.openFd(MODEL_FILE)
            return descriptor.use {
                FileInputStream(it.fileDescriptor).channel.use { channel ->
                    channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                }
            }
        }
    }
}
