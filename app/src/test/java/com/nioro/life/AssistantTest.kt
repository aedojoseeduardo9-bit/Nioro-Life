package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AssistantTest {
    @Test
    fun saludoSegunLaHora() {
        assertEquals("¡Hola, buenos días!", greeting(5))
        assertEquals("¡Hola, buenos días!", greeting(11))
        assertEquals("¡Hola, buenas tardes!", greeting(12))
        assertEquals("¡Hola, buenas tardes!", greeting(19))
        assertEquals("¡Hola, buenas noches!", greeting(20))
        assertEquals("¡Hola, buenas noches!", greeting(0))
        assertEquals("¡Hola, buenas noches!", greeting(4))
    }

    @Test
    fun nombresSinTildesNiMayusculas() {
        assertEquals("mama perez", normalizeName("  Mamá   Pérez "))
        assertEquals(100, nameScore("mama", "Mamá"))
        assertTrue(nameScore("juan", "Juan Carlos Soto") >= 75)
        assertTrue(nameScore("carlos", "Juan Carlos Soto") >= 75)
        assertTrue(nameScore("juan sot", "Juan Carlos Soto") > 0)
        assertEquals(0, nameScore("pedro", "Juan Carlos Soto"))
        assertEquals(0, nameScore("", "Juan"))
    }

    @Test
    fun numerosDeWhatsappDeChile() {
        assertEquals("56912345678", waNumber("9 1234 5678"))
        assertEquals("56912345678", waNumber("+56 9 1234 5678"))
        assertEquals("56912345678", waNumber("12345678"))
        assertEquals("56912345678", waNumber("0056912345678"))
        assertEquals("5491123456789", waNumber("+54 9 11 2345-6789"))
    }

    @Test
    fun horasYFechas() {
        assertEquals(18 * 60 + 30, parseHhMm("18:30"))
        assertEquals(7 * 60, parseHhMm("7"))
        assertEquals(6 * 60 + 5, parseHhMm("6.05"))
        assertNull(parseHhMm("25:00"))
        assertNull(parseHhMm("mañana"))

        val ms = parseDateTime("2026-11-20 18:30")!!
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        assertEquals(2026, c.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, c.get(Calendar.MONTH))
        assertEquals(20, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
        // sin hora: a las 9:00
        assertEquals(9, Calendar.getInstance().apply { timeInMillis = parseDateTime("2026-12-01")!! }.get(Calendar.HOUR_OF_DAY))
        assertNull(parseDateTime("2026-13-01 10:00"))
        assertNull(parseDateTime("el viernes"))
    }

    @Test
    fun coloresYBlancos() {
        assertEquals(0, colorHue("rojo"))
        assertEquals(230, colorHue("Azul"))
        assertEquals(330, colorHue("rosado"))
        assertEquals(275, colorHue("morado oscuro"))
        assertNull(colorHue("chartreuse"))
        assertTrue(whiteTemp("cálido") > whiteTemp("neutro"))
        assertTrue(whiteTemp("frío") < whiteTemp("neutro"))
        assertEquals(300, whiteTemp(null))
    }

    @Test
    fun modelosYErroresDeGemini() {
        assertTrue(usableModel("gemini-flash-latest"))
        assertTrue(usableModel("gemini-2.5-pro"))
        assertFalse(usableModel("gemini-2.5-flash-image"))
        assertFalse(usableModel("gemini-embedding-001"))
        assertFalse(usableModel("imagen-4.0"))
        assertTrue(modelRank("gemini-flash-latest") > modelRank("gemini-2.5-flash"))
        assertTrue(modelRank("gemini-2.5-flash") > modelRank("gemini-2.5-flash-lite"))
        assertTrue(geminiError(429, "RESOURCE_EXHAUSTED", "quota").contains("límite"))
        assertTrue(geminiError(400, "INVALID_ARGUMENT", "API key not valid").contains("clave"))
        assertTrue(geminiError(404, "NOT_FOUND", "x").contains("modelo"))
    }

    @Test
    fun textoParaLeerEnVozAlta() {
        assertEquals("Hola, listo", speakable("**Hola**, _listo_"))
        assertEquals("Mira esto", speakable("[Mira esto](https://x.cl)"))
    }
}
