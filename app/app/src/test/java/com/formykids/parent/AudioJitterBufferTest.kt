package com.formykids.parent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioJitterBufferTest {

    @Test
    fun `returns silence copy when empty`() {
        val silence = ByteArray(10) { 0 }
        val buf = AudioJitterBuffer(silenceChunk = silence)
        assertArrayEquals(silence, buf.poll())
    }

    @Test
    fun `returned silence is a copy not the original`() {
        val silence = ByteArray(4) { 0 }
        val buf = AudioJitterBuffer(silenceChunk = silence)
        val result = buf.poll()
        result[0] = 99
        assertEquals(0, buf.poll()[0].toInt())
    }

    @Test
    fun `returns offered chunk in FIFO order`() {
        val buf = AudioJitterBuffer()
        buf.offer(byteArrayOf(1))
        buf.offer(byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1), buf.poll())
        assertArrayEquals(byteArrayOf(2), buf.poll())
    }

    @Test
    fun `drops oldest chunk when full`() {
        val buf = AudioJitterBuffer(maxChunks = 2, silenceChunk = ByteArray(1))
        buf.offer(byteArrayOf(1))
        buf.offer(byteArrayOf(2))
        buf.offer(byteArrayOf(3))
        assertArrayEquals(byteArrayOf(2), buf.poll())
        assertArrayEquals(byteArrayOf(3), buf.poll())
    }

    @Test
    fun `size reflects queue depth`() {
        val buf = AudioJitterBuffer()
        assertEquals(0, buf.size())
        buf.offer(byteArrayOf(1))
        assertEquals(1, buf.size())
        buf.poll()
        assertEquals(0, buf.size())
    }

    @Test
    fun `clear empties the queue`() {
        val buf = AudioJitterBuffer(silenceChunk = ByteArray(2))
        buf.offer(byteArrayOf(1))
        buf.offer(byteArrayOf(2))
        buf.clear()
        assertEquals(0, buf.size())
        assertArrayEquals(ByteArray(2), buf.poll())
    }
}
