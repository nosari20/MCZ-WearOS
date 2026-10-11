package com.mczadvanced.wear

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `temperature has one decimal and degree sign`() {
        assertEquals("21.5°", formatTemp(21.5f))
        assertEquals("22.0°", formatTemp(22f))
        assertEquals("--", formatTemp(null))
    }

    @Test
    fun `age buckets`() {
        val now = 10_000_000L
        assertEquals("never", formatAge(null, now))
        assertEquals("just now", formatAge(now - 30_000, now))
        assertEquals("5 min ago", formatAge(now - 5 * 60_000, now))
        assertEquals("2 h ago", formatAge(now - 2 * 3_600_000, now))
    }
}
