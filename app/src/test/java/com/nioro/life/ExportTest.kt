package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class ExportTest {
    private fun ms(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    private val now = ms(2026, 10, 21)
    private val tx = listOf(
        Tx(1, 0, 485000, "Sueldo", "Trabajo", ms(2026, 10, 1), false),
        Tx(2, 1, 120000, "Arriendo; depto", "Vivienda", ms(2026, 10, 5), false),
        Tx(3, 2, 30000, "=Prestamo", "Amigo \"Juan\"", ms(2026, 10, 7), false),
        Tx(4, 1, 20000, "Super", "", ms(2026, 9, 15), false),
        Tx(5, 0, 400000, "Sueldo", "Trabajo", ms(2026, 9, 1), false)
    )

    @Test
    fun csvPorMes() {
        val all = Export.buildCsv(tx, Export.ALL_MONTHS, now)!!
        File("build").mkdirs()
        File("build/export-sample.csv").writeText(all)
        // septiembre aparece antes que octubre
        assertTrue(all.indexOf("Septiembre 2026") < all.indexOf("Octubre 2026"))
        // balance octubre: 485000 - 120000 - 30000 pendiente = 335000
        assertTrue(all.contains("Resumen: balance del mes;;;335000"))
        // balance septiembre: 400000 - 20000 = 380000
        assertTrue(all.contains("Resumen: balance del mes;;;380000"))
        // protección contra fórmulas y escape de ; y comillas
        assertTrue(all.contains("'=Prestamo"))
        assertTrue(all.contains("\"Arriendo; depto\""))
        assertTrue(all.contains("\"Amigo \"\"Juan\"\"\""))
        assertTrue(all.contains("Pendiente"))
    }

    @Test
    fun alcanceDeMes() {
        val oct = Export.buildCsv(tx, Export.THIS_MONTH, now)!!
        assertTrue(oct.contains("Octubre 2026"))
        assertTrue(!oct.contains("Septiembre 2026"))
        val sep = Export.buildCsv(tx, Export.LAST_MONTH, now)!!
        assertTrue(sep.contains("Septiembre 2026") && !sep.contains("Octubre 2026"))
        assertNull(Export.buildCsv(tx, Export.THIS_MONTH, ms(2026, 12, 3)))
        assertEquals(null, Export.buildCsv(emptyList(), Export.ALL_MONTHS, now))
    }
}
