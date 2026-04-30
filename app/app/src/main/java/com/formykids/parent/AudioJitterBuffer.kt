package com.formykids.parent

class AudioJitterBuffer(
    private val maxChunks: Int = 30,
    private val silenceChunk: ByteArray = ByteArray(3200)
) {
    private val queue = ArrayDeque<ByteArray>()

    @Synchronized
    fun offer(chunk: ByteArray) {
        if (queue.size >= maxChunks) queue.removeFirst()
        queue.addLast(chunk)
    }

    @Synchronized
    fun poll(): ByteArray = queue.removeFirstOrNull() ?: silenceChunk.copyOf()

    @Synchronized
    fun size(): Int = queue.size

    @Synchronized
    fun clear() = queue.clear()
}
