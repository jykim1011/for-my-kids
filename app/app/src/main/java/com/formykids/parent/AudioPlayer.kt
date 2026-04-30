package com.formykids.parent

import android.media.*
import kotlinx.coroutines.*

class AudioPlayer(private val seedChunks: Int = 3) {

    private val sampleRate = 16000
    private val track: AudioTrack
    private val jitterBuffer = AudioJitterBuffer()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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

    private suspend fun drainLoop() {
        while (jitterBuffer.size() < seedChunks) { delay(10) }
        while (currentCoroutineContext().isActive) {
            val chunk = jitterBuffer.poll()
            track.write(chunk, 0, chunk.size)
        }
    }

    fun write(pcmBytes: ByteArray) {
        jitterBuffer.offer(pcmBytes)
    }

    fun release() {
        scope.cancel()
        jitterBuffer.clear()
        track.stop()
        track.release()
    }
}
