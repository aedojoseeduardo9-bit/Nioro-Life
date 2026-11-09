package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartHomeTest {
    @Test
    fun coloresYTemperatura() {
        assertEquals(Triple(120, 100, 50), parseHsb("120,100,50"))
        assertNull(parseHsb("hola"))
        assertNull(parseHsb("1,2"))
        assertEquals(0xFFFF0000.toInt(), hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFF.toInt(), hsvToArgb(77f, 0f, 1f))
        assertEquals(6536, miredToKelvin(153))
        assertEquals(2000, miredToKelvin(500))
        assertEquals(370, kelvinToMired(2700))
        // un blanco cálido tiene menos azul que uno frío
        assertTrue((whiteArgb(500) and 0xFF) < (whiteArgb(153) and 0xFF))
    }

    @Test
    fun red() {
        assertEquals("http://192.168.1.5/cm?cmnd=Power%20Toggle", cmdUrl("192.168.1.5", "Power Toggle"))
        assertEquals("http://10.0.0.2/cm?cmnd=HSBColor%20120%2C100%2C50", cmdUrl("10.0.0.2", "HSBColor 120,100,50"))
        val h = subnetHosts("192.168.1.20")
        assertEquals(253, h.size)
        assertFalse(h.contains("192.168.1.20"))
        assertTrue(h.contains("192.168.1.1") && h.contains("192.168.1.254"))
        assertTrue(isIpv4("192.168.1.50"))
        assertFalse(isIpv4("192.168.1"))
        assertFalse(isIpv4("192.168.1.300"))
        assertFalse(isIpv4("a.b.c.d"))
    }

    @Test
    fun escenasValidas() {
        assertTrue(LIGHT_SCENES.size >= 6)
        LIGHT_SCENES.forEach {
            assertTrue(it.dimmer in 1..100)
            if (it.white) assertTrue(it.ct in 153..500) else assertTrue(it.hue in 0..360 && it.sat in 0..100)
        }
    }
}
