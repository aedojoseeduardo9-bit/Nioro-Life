package com.nioro.life

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class FinanceAITest {
    private fun ms(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    private val now = ms(2026, 10, 21)
    private val tx = listOf(
        Tx(1, 0, 485000, "Sueldo", "Trabajo", ms(2026, 10, 1), false),
        Tx(2, 1, 120000, "Arriendo", "Vivienda", ms(2026, 10, 5), false),
        Tx(3, 1, 5000, "Pan", "Comida", ms(2026, 10, 6), false),
        Tx(4, 2, 30000, "Prestamo", "Amigo", ms(2026, 10, 7), false),
        Tx(5, 1, 20000, "Super", "Comida", ms(2026, 9, 15), false)
    )
    private val cobros = listOf(
        Cobro(1, "Juan", 50000, "Venta", ms(2026, 10, 2), ms(2026, 10, 10), 10000),
        Cobro(2, "Ana", 20000, "Prestamo", ms(2026, 10, 3), null, 20000)
    )
    companion object {
        val log = StringBuilder()
    }

    private fun ask(q: String): String {
        val a = FinanceAI.answer(q, tx, cobros, now)
        log.append("P: $q\nR: $a\n\n")
        File("build").mkdirs()
        File("build/ai-sample.txt").writeText(log.toString())
        return a
    }

    private fun digits(s: String) = s.replace(Regex("[^0-9]"), "")

    @Test
    fun cuentas() {
        assertTrue(digits(ask("20% de 85000")).contains("17000"))
        assertTrue(digits(ask("45 lucas más iva")).contains("53550"))
        assertTrue(digits(ask("120000 en 6 cuotas")).contains("20000"))
        assertTrue(digits(ask("si gano 500000 y gasto 320000 cuánto me queda")).contains("180000"))
        assertTrue(digits(ask("450000+35000")).contains("485000"))
        assertTrue(ask("qué porcentaje es 30000 de 120000").contains("25"))
        assertTrue(digits(ask("descuento de 20% a 50000")).contains("40000"))
        assertTrue(digits(ask("ahorro 50000 al mes por 12 meses")).contains("600000"))
        assertTrue(digits(ask("dividir 90000 entre 4")).contains("22500"))
        assertTrue(digits(ask("2 millones menos 350.000")).contains("1650000"))
        assertTrue(digits(ask("(1500 + 2500) x 3")).contains("12000"))
    }

    @Test
    fun datos() {
        assertTrue(digits(ask("¿Cuánto gasté este mes?")).contains("125000"))
        assertTrue(digits(ask("cuánto gasté en comida este mes")).contains("5000"))
        assertTrue(digits(ask("cuánto gasté el mes pasado")).contains("20000"))
        assertTrue(digits(ask("¿Cuánto me deben?")).contains("40000"))
        assertTrue(digits(ask("cuál fue mi mayor gasto")).contains("120000"))
        assertTrue(digits(ask("cuánto gané este mes")).contains("485000"))
        assertTrue(digits(ask("cuánto debo")).contains("30000"))
        assertTrue(digits(ask("cuál es mi saldo")).contains("330000"))
        assertTrue(ask("blabla qué hora es").contains("No entendí"))
    }
}
