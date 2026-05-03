package com.formykids.child

import org.junit.Assert.*
import org.junit.Test

class RingBufferTest {

    @Test
    fun `read from empty buffer returns empty array`() {
        val buf = RingBuffer(100)
        assertTrue(buf.read(10).isEmpty())
    }

    @Test
    fun `read returns last N bytes when buffer not full`() {
        val buf = RingBuffer(1000)
        buf.write(ByteArray(50) { it.toByte() })
        val result = buf.read(10)
        assertEquals(10, result.size)
        assertEquals(40.toByte(), result[0])
        assertEquals(49.toByte(), result[9])
    }

    @Test
    fun `read returns last N bytes after wraparound`() {
        val buf = RingBuffer(100)
        buf.write(ByteArray(60) { it.toByte() })       // bytes 0..59
        buf.write(ByteArray(60) { (it + 60).toByte() }) // bytes 60..119; first 20 overwrite head
        val result = buf.read(60)
        assertEquals(60, result.size)
        assertEquals(60.toByte(), result[0])
        assertEquals(119.toByte(), result[59])
    }

    @Test
    fun `read capped at actual stored size`() {
        val buf = RingBuffer(100)
        buf.write(ByteArray(5) { it.toByte() })
        val result = buf.read(50)
        assertEquals(5, result.size)
    }
}
