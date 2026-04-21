package com.formykids

import org.junit.Assert.*
import org.junit.Test

class DangerDetectorTest {

    @Test
    fun `normalizeAudio converts 16-bit PCM to float32`() {
        val pcm = byteArrayOf(0x00, 0x40) // 16384 as little-endian short
        val floats = DangerDetector.normalizeAudio(pcm)
        assertEquals(1, floats.size)
        assertEquals(16384f / Short.MAX_VALUE, floats[0], 0.001f)
    }

    @Test
    fun `normalizeAudio returns empty for too-short input`() {
        val result = DangerDetector.normalizeAudio(byteArrayOf(0x01))
        assertEquals(0, result.size)
    }

    @Test
    fun `isDangerLabel identifies known danger labels`() {
        assertTrue(DangerDetector.isDangerLabel("Screaming"))
        assertTrue(DangerDetector.isDangerLabel("Crying, sobbing"))
        assertTrue(DangerDetector.isDangerLabel("Baby cry, infant cry"))
        assertTrue(DangerDetector.isDangerLabel("Shout"))
        assertFalse(DangerDetector.isDangerLabel("Speech"))
        assertFalse(DangerDetector.isDangerLabel("Music"))
    }
}
