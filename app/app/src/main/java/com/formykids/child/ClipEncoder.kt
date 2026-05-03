package com.formykids.child

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object ClipEncoder {

    private const val SAMPLE_RATE = 16000
    private const val BIT_RATE = 32_000
    private const val TIMEOUT_US = 10_000L

    fun calculateRms(pcmBytes: ByteArray): Float {
        if (pcmBytes.size < 2) return 0f
        val buf = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        var sumSq = 0.0
        val count = pcmBytes.size / 2
        repeat(count) {
            val s = buf.short.toDouble()
            sumSq += s * s
        }
        return (sqrt(sumSq / count) / Short.MAX_VALUE).toFloat()
    }

    fun encodeToAac(pcmBytes: ByteArray, outputFile: File) {
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var muxerStarted = false
        var inputOffset = 0
        var inputDone = false

        try {
            while (true) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (idx >= 0) {
                        val inputBuf = codec.getInputBuffer(idx)!!
                        val remaining = pcmBytes.size - inputOffset
                        if (remaining > 0) {
                            val n = minOf(remaining, inputBuf.capacity())
                            inputBuf.put(pcmBytes, inputOffset, n)
                            val pts = (inputOffset / 2).toLong() * 1_000_000L / SAMPLE_RATE
                            codec.queueInputBuffer(idx, 0, n, pts, 0)
                            inputOffset += n
                        } else {
                            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                when (val outputIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> { /* retry */ }
                    else -> if (outputIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outputIdx)!!
                        val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (muxerStarted && info.size > 0 && !isConfig) {
                            muxer.writeSampleData(trackIndex, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outputIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()
        }
    }
}
