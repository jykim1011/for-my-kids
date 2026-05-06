package com.formykids.parent

import android.media.*
import kotlinx.coroutines.*
import io.github.jaredmdobson.concentus.OpusDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioPlayer(private val seedChunks: Int = 3) {

    private val sampleRate = 16000
    private val opusFrameSize = 320 // 20ms at 16kHz
    private val decoder = OpusDecoder(sampleRate, 1)
    private val track: AudioTrack
    private val jitterBuffer = AudioJitterBuffer(silenceChunk = ByteArray(640))
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        scope.launch { drainLoop() }
    }

    fun setPreferredDevice(device: AudioDeviceInfo?) {
        track.preferredDevice = device
    }

    private suspend fun drainLoop() {
        while (jitterBuffer.size() < seedChunks) { delay(10) }
        while (currentCoroutineContext().isActive) {
            val chunk = jitterBuffer.poll()
            track.write(chunk, 0, chunk.size)
        }
    }

    @Synchronized
    fun write(opusBytes: ByteArray) {
        val outShorts = ShortArray(opusFrameSize * 2)
        val samples: Int = try {
            decoder.decode(opusBytes, 0, opusBytes.size, outShorts, 0, opusFrameSize, false)
        } catch (e: Exception) { return }
        if (samples <= 0) return
        val pcmBytes = ByteArray(samples * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(outShorts, 0, samples)
        jitterBuffer.offer(pcmBytes)
    }

    fun release() {
        scope.cancel()
        jitterBuffer.clear()
        track.stop()
        track.release()
    }
}
