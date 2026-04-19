package com.formykids

import com.formykids.parent.VolumeAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VolumeAnalyzerTest {

    private fun shortArrayToPcm(shorts: ShortArray): ByteArray {
        val buf = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        shorts.forEach { buf.putShort(it) }
        return buf.array()
    }

    @Test
    fun `silence returns 0`() {
        val pcm = shortArrayToPcm(ShortArray(100) { 0 })
        assertEquals(0f, VolumeAnalyzer.rms(pcm), 0.001f)
    }

    @Test
    fun `max amplitude returns near 1`() {
        val pcm = shortArrayToPcm(ShortArray(100) { Short.MAX_VALUE })
        assertTrue(VolumeAnalyzer.rms(pcm) > 0.99f)
    }

    @Test
    fun `empty array returns 0`() {
        assertEquals(0f, VolumeAnalyzer.rms(ByteArray(0)), 0.001f)
    }
}
