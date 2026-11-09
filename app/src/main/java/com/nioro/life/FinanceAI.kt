package com.nioro.life

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/**
 * Asistente local de Finanzas. No usa internet ni envía nada fuera del teléfono:
 * entiende frases en español sobre cuentas ("20% de 85000", "45 lucas más iva")
 * y preguntas sobre tus propios datos ("cuánto gasté este mes", "quién me debe").
 */
object FinanceAI {
    private val MONTHS = listOf(
        "enero", "febrero", "marzo", "abril", "mayo", "junio",
        "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
    )

    val SUGGESTIONS = listOf(
        "¿Cuánto gasté este mes?",
        "¿Cuánto me deben?",
        "20% de 85000",
        "45 lucas más iva",
        "120000 en 6 cuotas"
    )

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    private fun money(v: Long): String = fmtMoney(v)

    private fun num(v: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale("es", "CL"))
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = 0
        return nf.format(v)
    }

    private fun monthKey(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun monthName(key: Int): String =
        MONTHS[key % 12].replaceFirstChar { it.uppercase() } + " " + (key / 12)

    // ------------------------------------------------------------------ números

    /** Deja los números como "12345.5": quita puntos de miles, coma decimal, "mil", "lucas", "palos"… */
    private fun normalizeNumbers(input: String): String {
        var t = input
        t = t.replace("$", " ")
        // 1.234.567 -> 1234567
        while (Regex("(\\d)\\.(\\d{3})(?!\\d)").containsMatchIn(t)) {
            t = t.replace(Regex("(\\d)\\.(\\d{3})(?!\\d)"), "$1$2")
        }
        // 1,5 -> 1.5
        t = t.replace(Regex("(\\d),(\\d)"), "$1.$2")
        // 5 mil, 2 millones, 20 lucas, 1.5 palos, 50k
        t = Regex("(\\d+(?:\\.\\d+)?)\\s*(millones|millon|palos|palo|mil|lucas|luca|k)\\b").replace(t) { m ->
            val base = m.groupValues[1].toDouble()
            val mult = when (m.groupValues[2]) {
                "millones", "millon", "palos", "palo" -> 1_000_000.0
                else -> 1_000.0
            }
            val r = base * mult
            if (r == Math.floor(r)) r.toLong().toString() else r.toString()
        }
        return t
    }

    private val NUM = "(\\d+(?:\\.\\d+)?)"

    /** Convierte la frase en una cuenta como "(20/100*85000)" o "45000*1.19". Devuelve null si no hay cuenta. */
    private fun toExpression(q: String): String? {
        var t = " " + normalizeNumbers(norm(q)) + " "

        // si gano A y gasto B -> A-B
        t = Regex("si (?:gano|tengo|recibo|cobro|me pagan) $NUM\\D+?(?:gasto|pago|debo|me descuentan|compro) $NUM")
            .replace(t) { " ${it.groupValues[1]}-${it.groupValues[2]} " }

        // descuento de X% a Y
        t = Regex("descuento (?:de |del )?$NUM\\s*%\\s*(?:a|en|de|sobre|al)\\s*$NUM")
            .replace(t) { " (${it.groupValues[2]}*(1-${it.groupValues[1]}/100)) " }

        // X% de Y
        t = Regex("$NUM\\s*%\\s*de\\s*$NUM")
            .replace(t) { " (${it.groupValues[1]}/100*${it.groupValues[2]}) " }

        // IVA (19 %)
        t = Regex("$NUM\\s*(?:mas|con|\\+)\\s*iva").replace(t) { " (${it.groupValues[1]}*1.19) " }
        t = Regex("$NUM\\s*(?:sin|menos)\\s*iva").replace(t) { " (${it.groupValues[1]}/1.19) " }
        t = Regex("iva (?:de|del)\\s*$NUM").replace(t) { " (${it.groupValues[1]}*0.19) " }

        // X en N cuotas
        t = Regex("$NUM\\s*(?:en|entre|a)\\s*(\\d+)\\s*cuotas?").replace(t) { " (${it.groupValues[1]}/${it.groupValues[2]}) " }

        // A mas/menos B%
        t = Regex("$NUM\\s*(?:mas|\\+)\\s*$NUM\\s*%").replace(t) { " (${it.groupValues[1]}*(1+${it.groupValues[2]}/100)) " }
        t = Regex("$NUM\\s*(?:menos|-)\\s*$NUM\\s*%").replace(t) { " (${it.groupValues[1]}*(1-${it.groupValues[2]}/100)) " }

        // unidades pegadas a un número: "12 meses", "4 personas", "al mes"
        t = Regex("(\\d)\\s+al\\s+mes").replace(t, "$1")
        t = Regex("(\\d)\\s*(?:meses|mes|dias|dia|semanas|semana|personas|persona|cuotas|cuota|pesos|peso)\\b").replace(t, "$1")

        // palabras -> operadores
        t = t.replace(Regex("\\s+(?:multiplicado por|multiplicado|veces|por|x)\\s+"), " * ")
        t = t.replace(Regex("\\s+(?:dividido por|dividido en|dividido|entre|sobre)\\s+"), " / ")
        t = t.replace(Regex("\\s+(?:mas|sumado a|sumar)\\s+"), " + ")
        t = t.replace(Regex("\\s+(?:menos|restar|restado)\\s+"), " - ")
        t = t.replace("×", "*").replace("÷", "/").replace("−", "-")

        // buscar tramos que parezcan una cuenta
        val candidates = Regex("[0-9(][0-9+\\-*/(). ]*[0-9)]").findAll(t)
            .map { it.value.trim() }
            .filter { c -> c.any { it in "+-*/" } || (c.startsWith("(") && c.endsWith(")")) }
            .sortedByDescending { it.length }
            .toList()
        for (c in candidates) {
            if (evalPlain(c) != null) return c
        }
        return null
    }

    private fun looksLikeMoney(q: String): Boolean {
        val n = norm(q)
        return listOf("$", "peso", "luca", "palo", "iva", "cuota", "descuento", "plata", "gano", "gasto", "pago", "ahorro")
            .any { n.contains(it) }
    }

    private fun mathAnswer(q: String): String? {
        val n = normalizeNumbers(norm(q))
        // qué porcentaje es A de B
        Regex("porcentaje (?:es|son|representa) $NUM de $NUM").find(n)?.let { m ->
            val a = m.groupValues[1].toDouble()
            val b = m.groupValues[2].toDouble()
            if (b == 0.0) return "No se puede dividir por cero."
            return "${num(a)} es el ${num(a / b * 100)} % de ${num(b)}."
        }
        val expr = toExpression(q) ?: return null
        val v = evalPlain(expr) ?: return null
        val shown = if (looksLikeMoney(q)) money(Math.round(v)) else num(v)
        val pretty = expr.replace("*", " × ").replace("/", " ÷ ").replace("+", " + ").replace("-", " − ")
            .replace(Regex("\\s+"), " ").trim()
        return "Resultado: $shown\nCuenta que entendí: $pretty"
    }

    // ------------------------------------------------------------------ preguntas sobre tus datos

    /** Mes pedido en la frase (mes pasado, nombre de un mes) o el actual. */
    private fun targetMonth(n: String, now: Long): Int {
        val cur = monthKey(now)
        if (Regex("mes pasado|mes anterior|ultimo mes").containsMatchIn(n)) return cur - 1
        for ((i, name) in MONTHS.withIndex()) {
            if (Regex("\\b$name\\b").containsMatchIn(n)) {
                val year = cur / 12
                val k = year * 12 + i
                return if (k > cur) k - 12 else k
            }
        }
        return cur
    }

    private fun spent(tx: List<Tx>, key: Int): List<Tx> =
        tx.filter { monthKey(it.date) == key && (it.kind == 1 || (it.kind == 2 && it.paid)) }

    private fun searchTerm(n: String): String? {
        val m = Regex("gast\\w*\\s+(?:en|de|por|con|para)\\s+(.+)$").find(n) ?: return null
        var term = m.groupValues[1]
        term = term.replace(Regex("(este mes|el mes pasado|mes pasado|mes anterior|del mes|este ano|\\?)"), " ")
        for (name in MONTHS) term = term.replace(Regex("\\b$name\\b"), " ")
        term = term.replace(Regex("\\b(el|la|los|las|un|una|unos|unas|mi|mis|del|de|en)\\b"), " ")
        term = term.replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
        return if (term.length >= 3) term else null
    }

    private fun dataAnswer(q: String, tx: List<Tx>, cobros: List<Cobro>, now: Long): String? {
        val n = norm(q)
        val key = targetMonth(n, now)
        val label = monthName(key)
        val sum = monthSummary(tx, run {
            val c = Calendar.getInstance()
            c.timeInMillis = now
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, key - monthKey(now))
            c.timeInMillis
        })

        // cobranzas
        if (Regex("deben|debe\\b|cobrar|cobranza|me adeudan").containsMatchIn(n) && !n.contains("debo")) {
            val open = cobros.filter { !it.settled }
            if (open.isEmpty()) return "Nadie te debe nada por ahora. 🎉"
            val total = open.sumOf { it.remaining }
            val overdue = open.filter { (it.due ?: Long.MAX_VALUE) < now }
            val top = open.sortedByDescending { it.remaining }.take(3)
                .joinToString("\n") { "• ${it.person.ifBlank { "Sin nombre" }}: ${money(it.remaining)}" }
            return "Te deben ${money(total)} en total (${open.size} ${if (open.size == 1) "cobranza" else "cobranzas"}).\n" +
                (if (overdue.isNotEmpty()) "Vencidas: ${overdue.size}.\n" else "") + top
        }

        // mayor gasto
        if (Regex("mayor gasto|gasto mas (?:grande|alto)|en que gaste mas").containsMatchIn(n)) {
            val big = spent(tx, key).maxByOrNull { it.amount } ?: return "No hay gastos en $label."
            return "Tu mayor gasto de $label fue ${big.title.ifBlank { "Gasto" }}: ${money(big.amount)} (${fmtDay(big.date)})."
        }

        // gastos (con o sin filtro)
        if (Regex("gast|gaste|he gastado").containsMatchIn(n) && !n.contains("gasto mas")) {
            val list = spent(tx, key)
            val term = searchTerm(n)
            val filtered = if (term != null) list.filter { norm(it.title + " " + it.reason).contains(term) } else list
            if (filtered.isEmpty()) {
                return if (term != null) "No encontré gastos de «$term» en $label." else "No tienes gastos registrados en $label."
            }
            val total = filtered.sumOf { it.amount }
            return if (term != null) {
                "En $label gastaste ${money(total)} en «$term» (${filtered.size} ${if (filtered.size == 1) "movimiento" else "movimientos"})."
            } else {
                "En $label llevas ${money(total)} en gastos (${filtered.size} ${if (filtered.size == 1) "movimiento" else "movimientos"})."
            }
        }

        // ingresos
        if (Regex("ingres|gane|gano|sueldo|recibi").containsMatchIn(n)) {
            return if (sum.income == 0L) "No tienes ingresos registrados en $label."
            else "En $label tus ingresos suman ${money(sum.income)}."
        }

        // deudas mías
        if (Regex("\\bdebo\\b|mis deudas|deudas pendientes|cuanto adeudo").containsMatchIn(n)) {
            val open = tx.filter { it.kind == 2 && !it.paid }
            if (open.isEmpty()) return "No tienes deudas pendientes."
            val total = open.sumOf { it.amount }
            val top = open.sortedByDescending { it.amount }.take(3)
                .joinToString("\n") { "• ${it.title.ifBlank { "Deuda" }}: ${money(it.amount)}" }
            return "Debes ${money(total)} en total (${open.size} ${if (open.size == 1) "deuda" else "deudas"}).\n$top"
        }

        // promedio mensual de gasto
        if (n.contains("promedio")) {
            val months = tx.filter { it.kind == 1 || (it.kind == 2 && it.paid) }.groupBy { monthKey(it.date) }
            if (months.isEmpty()) return "Aún no hay gastos para calcular un promedio."
            val avg = months.values.map { l -> l.sumOf { it.amount } }.average()
            return "Gastas en promedio ${money(Math.round(avg))} al mes (${months.size} ${if (months.size == 1) "mes" else "meses"} con datos)."
        }

        // saldo / resumen
        if (Regex("saldo|balance|me queda|cuanto tengo|resumen|como voy").containsMatchIn(n)) {
            return "$label:\n• Ingresos: ${money(sum.income)}\n• Gastos: ${money(sum.expenses)}\n" +
                "• Deudas pendientes: ${money(sum.pending)}\n• Saldo: ${money(sum.balance)}"
        }
        return null
    }

    fun answer(q: String, tx: List<Tx>, cobros: List<Cobro>, now: Long): String {
        if (q.isBlank()) return "Escríbeme una cuenta o una pregunta."
        mathAnswer(q)?.let { return it }
        dataAnswer(q, tx, cobros, now)?.let { return it }
        return "No entendí eso. Prueba con una cuenta («20% de 85000», «45 lucas más iva», «120000 en 6 cuotas») " +
            "o una pregunta («¿cuánto gasté este mes?», «¿cuánto me deben?», «¿cuál fue mi mayor gasto?»)."
    }
}
