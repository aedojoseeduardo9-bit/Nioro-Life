package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListsTest {
    private fun list(kind: Int, vararg items: String) =
        CheckList(1, "Test", kind, items.mapIndexed { i, t -> CheckItem(i.toLong(), t, 0, false) }, 0)

    @Test
    fun historialYSugerencias() {
        var h: Map<Int, List<HistItem>> = emptyMap()
        h = recordHistory(h, 0, "Leche", 1500)
        h = recordHistory(h, 0, "leche", 0)
        h = recordHistory(h, 0, "Queso", 4000)
        val leche = h[0]!!.first { it.text.lowercase() == "leche" }
        assertEquals(2, leche.count)
        assertEquals(1500L, leche.price) // recuerda el último precio conocido

        val s = suggestionsFor(list(0, "Queso"), h)
        assertEquals("leche", s.first().text.lowercase())          // lo más usado primero
        assertFalse(s.any { it.text.lowercase() == "queso" })        // no repite lo que ya está
        assertTrue(s.any { it.text == "Pan" })                       // ideas de ejemplo
        assertTrue(s.size <= 14)
        // el tipo Deseos no tiene ideas de ejemplo ni historial
        assertTrue(suggestionsFor(list(3), h).isEmpty())
    }
}
