package com.formykids

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class DangerDetector(context: Context, private val confidenceThreshold: Float = 0.6f) : AutoCloseable {

    private val interpreter: Interpreter
    private val labels: List<String>

    init {
        val afd = context.assets.openFd("yamnet.tflite")
        val model = afd.createInputStream().channel.map(
            FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength
        )
        interpreter = Interpreter(model)
        labels = context.assets.open("yamnet_labels.txt").use { stream ->
            BufferedReader(InputStreamReader(stream)).readLines()
        }
    }

    data class Detection(val label: String, val confidence: Float, val type: String)

    fun analyze(pcmBytes: ByteArray): Detection? {
        val floats = normalizeAudio(pcmBytes)
        if (floats.size < SAMPLE_SIZE) return null

        val input = Array(1) { floats.copyOf(SAMPLE_SIZE) }
        val outputScores = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, outputScores)

        val scores = outputScores[0]
        val maxIdx = scores.indices.maxByOrNull { scores[it] } ?: return null
        val maxScore = scores[maxIdx]
        val label = labels.getOrNull(maxIdx) ?: return null

        if (maxScore < confidenceThreshold || !isDangerLabel(label)) return null
        return Detection(label, maxScore, labelToType(label))
    }

    override fun close() = interpreter.close()

    companion object {
        const val SAMPLE_RATE = 16000
        const val SAMPLE_SIZE = 15600

        private val DANGER_LABELS = setOf(
            "Screaming", "Shout", "Bellow", "Whoop",
            "Crying, sobbing", "Baby cry, infant cry", "Whimper, whine",
            "Wail, moan"
        )

        fun normalizeAudio(pcmBytes: ByteArray): FloatArray {
            if (pcmBytes.size < 2) return FloatArray(0)
            val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(pcmBytes.size / 2) { buf.short.toFloat() / Short.MAX_VALUE }
        }

        fun isDangerLabel(label: String): Boolean =
            DANGER_LABELS.any { label.contains(it, ignoreCase = true) }

        private fun labelToType(label: String): String = when {
            label.contains("scream", true) || label.contains("shout", true) -> "scream"
            label.contains("cry", true) || label.contains("wail", true) || label.contains("whimper", true) -> "cry"
            else -> "loud"
        }
    }
}
