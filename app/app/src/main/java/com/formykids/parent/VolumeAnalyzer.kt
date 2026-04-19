package com.formykids.parent

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VolumeAnalyzer {
    fun rms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(pcmBytes.size / 2) { buf.short }
        val sumSq = shorts.sumOf { it.toLong() * it.toLong() }
        return (sqrt(sumSq.toDouble() / shorts.size) / Short.MAX_VALUE).toFloat()
    }
}
