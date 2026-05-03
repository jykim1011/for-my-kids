package com.formykids.child

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ClipEncoderTest {

    @Test
    fun `silent audio returns zero RMS`() {
        val silent = ByteArray(3200)
        assertEquals(0f, ClipEncoder.calculateRms(silent), 0.001f)
    }

    @Test
    fun `max amplitude audio returns near-one RMS`() {
        val buf = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN)
        repeat(1600) { buf.putShort(Short.MAX_VALUE) }
        val rms = ClipEncoder.calculateRms(buf.array())
        assertTrue("Expected RMS > 0.99, got $rms", rms > 0.99f)
    }

    @Test
    fun `empty array returns zero`() {
        assertEquals(0f, ClipEncoder.calculateRms(ByteArray(0)), 0f)
    }

    @Test
    fun `single byte array returns zero`() {
        assertEquals(0f, ClipEncoder.calculateRms(ByteArray(1)), 0f)
    }
}
