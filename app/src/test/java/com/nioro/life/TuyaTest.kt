package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TuyaTest {
    @Test
    fun firmaComoLaDeTuya() {
        val empty = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(empty, sha256Hex(""))
        assertEquals("/v1.0/token?grant_type=1", tuyaUrl("/v1.0/token", mapOf("grant_type" to "1")))
        assertEquals("/x?a=1&b=2&c=3", tuyaUrl("/x", mapOf("c" to "3", "a" to "1", "b" to "2")))
        val sts = tuyaStringToSign("GET", "", "/v1.0/token?grant_type=1")
        assertEquals("GET\n$empty\n\n/v1.0/token?grant_type=1", sts)
        // calculado aparte con Python (hmac + hashlib)
        assertEquals(
            "6C5620195C02306ED28815809AFFF6FD90840845936EEBF4DEEC3895CB8A0065",
            tuyaSign("clientabc", "secret123", "", "1588925778000", "", sts)
        )
        val body = toJson(mapOf("commands" to listOf(mapOf("code" to "switch_led", "value" to true))))
        assertEquals("{\"commands\":[{\"code\":\"switch_led\",\"value\":true}]}", body)
        assertEquals(
            "992A72EF38F89550DD28D29EFA16491394E16FADC946C0636ACE5864A3075340",
            tuyaSign("clientabc", "secret123", "tok999", "1588925778000", "", tuyaStringToSign("POST", body, "/v1.0/devices/dev1/commands"))
        )
    }

    private fun bulb(vararg st: Pair<String, Any?>) = TuyaDevice("dev1", "Living", "dj", true, "Bulb", mapOf(*st))

    @Test
    fun estadoDeUnaAmpolletaV2() {
        val color = bulb(
            "switch_led" to true, "work_mode" to "colour", "bright_value_v2" to 500, "temp_value_v2" to 0,
            "colour_data_v2" to "{\"h\":120,\"s\":1000,\"v\":800}", "scene_data_v2" to "{}", "countdown_1" to 0
        )
        val s = tuyaState(color)
        assertTrue(s.on && s.online)
        assertFalse(s.white)
        assertEquals(120, s.hue)
        assertEquals(100, s.sat)
        assertEquals(80, s.dimmer)
        val caps = tuyaCaps(color)
        assertTrue(caps.light && caps.color && caps.white && caps.effects && caps.timer)

        val white = tuyaState(bulb("switch_led" to false, "work_mode" to "white", "bright_value_v2" to 300, "temp_value_v2" to 1000))
        assertFalse(white.on)
        assertTrue(white.white)
        assertEquals(30, white.dimmer)
        assertEquals(153, white.ct) // 1000 = el más frío
        assertEquals(500, tuyaState(bulb("switch_led" to true, "temp_value_v2" to 0, "bright_value_v2" to 10)).ct)
    }

    @Test
    fun ordenesParaTuya() {
        val d = bulb("switch_led" to true, "work_mode" to "white", "bright_value_v2" to 500, "temp_value_v2" to 500, "colour_data_v2" to "{\"h\":0,\"s\":0,\"v\":1000}")
        val off = BulbState(online = true, on = false)
        assertEquals(listOf("switch_led" to true), tuyaCommands(d, LightCmd.Power(true), off))
        // si estaba apagada, el color la enciende primero
        val c = tuyaCommands(d, LightCmd.Hsv(200, 50, 40), off)
        assertEquals("switch_led" to true, c[0])
        assertEquals("work_mode" to "colour", c[1])
        assertEquals("colour_data_v2" to mapOf("h" to 200, "s" to 500, "v" to 400), c[2])
        val on = BulbState(online = true, on = true, white = true)
        assertEquals(listOf("bright_value_v2" to 700), tuyaCommands(d, LightCmd.Level(70), on))
        val w = tuyaCommands(d, LightCmd.White(153, 100), on)
        assertTrue(w.contains("temp_value_v2" to 1000) && w.contains("bright_value_v2" to 1000) && w.contains("work_mode" to "white"))
        val fx = tuyaCommands(d, LightCmd.Fx(LIGHT_EFFECTS[0]), on)
        assertEquals("work_mode" to "scene", fx[0])
        assertTrue(toJson(fx[1].second).contains("\"unit_change_mode\":\"gradient\""))
        // un enchufe solo tiene encendido
        val plug = TuyaDevice("p1", "Enchufe", "cz", true, "Plug", mapOf("switch_1" to false, "countdown_1" to 0))
        assertFalse(tuyaCaps(plug).light)
        assertEquals(listOf("switch_1" to true), tuyaCommands(plug, LightCmd.Power(true), off))
        assertEquals(listOf("countdown_1" to 1800), tuyaCommands(plug, LightCmd.Timer(1800), off))
    }

    @Test
    fun efectosValidos() {
        assertTrue(LIGHT_EFFECTS.size >= 6)
        LIGHT_EFFECTS.forEach { fx ->
            assertTrue(fx.colors.size >= 2)
            assertTrue(fx.mode in setOf("gradient", "jump", "static"))
            assertTrue(fx.speed in 1..100)
        }
        assertEquals(TUYA_REGIONS.size, TUYA_REGIONS.map { it.second }.toSet().size)
    }
}
