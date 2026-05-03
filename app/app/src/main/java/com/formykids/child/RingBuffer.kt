package com.formykids.child

class RingBuffer(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var size = 0

    @Synchronized
    fun write(data: ByteArray) {
        var remaining = data.size
        var srcOffset = 0
        while (remaining > 0) {
            val tail = (head + size) % capacity
            val toWrite = minOf(remaining, capacity - tail)
            System.arraycopy(data, srcOffset, buffer, tail, toWrite)
            srcOffset += toWrite
            remaining -= toWrite
            if (size + toWrite <= capacity) {
                size += toWrite
            } else {
                val overflow = (size + toWrite) - capacity
                head = (head + overflow) % capacity
                size = capacity
            }
        }
    }

    @Synchronized
    fun read(length: Int): ByteArray {
        val actual = minOf(length, size)
        if (actual == 0) return ByteArray(0)
        val result = ByteArray(actual)
        val startIdx = (head + size - actual + capacity) % capacity
        val firstChunk = minOf(actual, capacity - startIdx)
        System.arraycopy(buffer, startIdx, result, 0, firstChunk)
        if (firstChunk < actual) {
            System.arraycopy(buffer, 0, result, firstChunk, actual - firstChunk)
        }
        return result
    }
}
