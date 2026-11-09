package com.nioro.life

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

const val ALL_DAYS = 127

data class Note(
    val id: Long,
    val title: String,
    val text: String,
    val remindAt: Long?,
    val done: Boolean
)

data class Settings(
    val bedMin: Int = 23 * 60,
    val wakeMin: Int = 7 * 60,
    val alarmOn: Boolean = true,
    val bedNotify: Boolean = false,
    val leadMin: Int = 30,
    /** Tema: 0 noche, 1 día, 2 rosa, 3 rojo, 4 morado. */
    val theme: Int = 0,
    /** Días en que suena la alarma (bits: lunes = 1 … domingo = 64). */
    val wakeDays: Int = ALL_DAYS
)

/** kind: 0 = ingreso, 1 = gasto, 2 = deuda. */
data class Tx(
    val id: Long,
    val kind: Int,
    val amount: Long,
    val title: String,
    val reason: String,
    val date: Long,
    val paid: Boolean
)

data class SleepState(val sleeping: Boolean, val fromMs: Long, val targetMs: Long)

/** Dinero que otras personas te deben (cobranzas). `paidAmt` acumula los abonos. */
data class Cobro(
    val id: Long,
    val person: String,
    val amount: Long,
    val reason: String,
    val date: Long,
    val due: Long?,
    val paidAmt: Long
) {
    val remaining: Long get() = maxOf(0L, amount - paidAmt)
    val settled: Boolean get() = remaining <= 0L
}

data class CheckItem(val id: Long, val text: String, val price: Long, val done: Boolean)

/** kind: 0 compras, 1 repuestos, 2 equipos, 3 deseos, 4 otra. */
data class CheckList(
    val id: Long,
    val title: String,
    val kind: Int,
    val items: List<CheckItem>,
    val created: Long
)

/** Cosas que ya agregaste antes a listas de un tipo; sirven de sugerencias. */
data class HistItem(val text: String, val price: Long, val count: Int)

val LIST_KIND_NAMES = listOf("Compras", "Repuestos", "Equipos", "Deseos", "Otra")
val LIST_KIND_EMOJI = listOf("🛒", "🔧", "💻", "⭐", "📋")

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("nioro", Context.MODE_PRIVATE)

    fun loadSettings(c: Context): Settings {
        val p = prefs(c)
        return Settings(
            bedMin = p.getInt("bed", 23 * 60),
            wakeMin = p.getInt("wake", 7 * 60),
            alarmOn = p.getBoolean("alarm", true),
            bedNotify = p.getBoolean("bedNotify", false),
            leadMin = p.getInt("lead", 30),
            theme = p.getInt("theme", if (p.getBoolean("light", false)) 1 else 0),
            wakeDays = p.getInt("wakeDays", ALL_DAYS)
        )
    }

    fun saveSettings(c: Context, s: Settings) {
        prefs(c).edit()
            .putInt("bed", s.bedMin)
            .putInt("wake", s.wakeMin)
            .putBoolean("alarm", s.alarmOn)
            .putBoolean("bedNotify", s.bedNotify)
            .putInt("lead", s.leadMin)
            .putInt("theme", s.theme)
            .putInt("wakeDays", s.wakeDays)
            .apply()
    }

    fun loadNotes(c: Context): List<Note> {
        val raw = prefs(c).getString("notes", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Note(
                    id = o.getLong("id"),
                    title = o.optString("title", ""),
                    text = o.optString("text", ""),
                    remindAt = if (o.has("remindAt") && !o.isNull("remindAt")) o.getLong("remindAt") else null,
                    done = o.optBoolean("done", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveNotes(c: Context, list: List<Note>) {
        val arr = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("text", n.text)
            if (n.remindAt != null) o.put("remindAt", n.remindAt)
            o.put("done", n.done)
            arr.put(o)
        }
        prefs(c).edit().putString("notes", arr.toString()).apply()
    }

    fun loadTx(c: Context): List<Tx> {
        val raw = prefs(c).getString("tx", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Tx(
                    id = o.getLong("id"),
                    kind = o.optInt("kind", 1),
                    amount = o.optLong("amount", 0L),
                    title = o.optString("title", ""),
                    reason = o.optString("reason", ""),
                    date = o.optLong("date", System.currentTimeMillis()),
                    paid = o.optBoolean("paid", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveTx(c: Context, list: List<Tx>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("kind", t.kind)
            o.put("amount", t.amount)
            o.put("title", t.title)
            o.put("reason", t.reason)
            o.put("date", t.date)
            o.put("paid", t.paid)
            arr.put(o)
        }
        prefs(c).edit().putString("tx", arr.toString()).apply()
    }

    fun loadCobros(c: Context): List<Cobro> {
        val raw = prefs(c).getString("cobros", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Cobro(
                    id = o.getLong("id"),
                    person = o.optString("person", ""),
                    amount = o.optLong("amount", 0L),
                    reason = o.optString("reason", ""),
                    date = o.optLong("date", System.currentTimeMillis()),
                    due = if (o.has("due") && !o.isNull("due")) o.getLong("due") else null,
                    paidAmt = o.optLong("paidAmt", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCobros(c: Context, list: List<Cobro>) {
        val arr = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("person", t.person)
            o.put("amount", t.amount)
            o.put("reason", t.reason)
            o.put("date", t.date)
            if (t.due != null) o.put("due", t.due)
            o.put("paidAmt", t.paidAmt)
            arr.put(o)
        }
        prefs(c).edit().putString("cobros", arr.toString()).apply()
    }

    fun loadLists(c: Context): List<CheckList> {
        val raw = prefs(c).getString("lists", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val its = o.optJSONArray("items") ?: JSONArray()
                CheckList(
                    id = o.getLong("id"),
                    title = o.optString("title", ""),
                    kind = o.optInt("kind", 4),
                    created = o.optLong("created", 0L),
                    items = (0 until its.length()).map { j ->
                        val q = its.getJSONObject(j)
                        CheckItem(
                            id = q.getLong("id"),
                            text = q.optString("text", ""),
                            price = q.optLong("price", 0L),
                            done = q.optBoolean("done", false)
                        )
                    }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLists(c: Context, list: List<CheckList>) {
        val arr = JSONArray()
        for (l in list) {
            val o = JSONObject()
            o.put("id", l.id)
            o.put("title", l.title)
            o.put("kind", l.kind)
            o.put("created", l.created)
            val its = JSONArray()
            for (q in l.items) {
                val x = JSONObject()
                x.put("id", q.id)
                x.put("text", q.text)
                x.put("price", q.price)
                x.put("done", q.done)
                its.put(x)
            }
            o.put("items", its)
            arr.put(o)
        }
        prefs(c).edit().putString("lists", arr.toString()).apply()
    }

    fun loadHistory(c: Context): Map<Int, List<HistItem>> {
        val raw = prefs(c).getString("listHistory", "{}") ?: "{}"
        return try {
            val o = JSONObject(raw)
            val out = HashMap<Int, List<HistItem>>()
            for (k in o.keys()) {
                val arr = o.getJSONArray(k)
                out[k.toInt()] = (0 until arr.length()).map { i ->
                    val q = arr.getJSONObject(i)
                    HistItem(q.optString("t", ""), q.optLong("p", 0L), q.optInt("n", 1))
                }
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun saveHistory(c: Context, h: Map<Int, List<HistItem>>) {
        val o = JSONObject()
        for ((k, list) in h) {
            val arr = JSONArray()
            for (q in list) {
                val x = JSONObject()
                x.put("t", q.text)
                x.put("p", q.price)
                x.put("n", q.count)
                arr.put(x)
            }
            o.put(k.toString(), arr)
        }
        prefs(c).edit().putString("listHistory", o.toString()).apply()
    }
}

fun fmtTime(minOfDay: Int): String = "%02d:%02d".format(minOfDay / 60, minOfDay % 60)

fun fmtMoney(v: Long): String {
    val n = NumberFormat.getIntegerInstance(Locale("es", "CL")).format(Math.abs(v))
    return (if (v < 0) "-" else "") + "$" + n
}

fun sleepDurMin(s: Settings): Int {
    val d = ((s.wakeMin - s.bedMin) + 1440) % 1440
    return if (d == 0) 1440 else d
}

fun fmtDur(min: Int): String {
    val h = min / 60
    val m = min % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

fun dayAt(base: Long, minOfDay: Int, dayOffset: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = base
    c.add(Calendar.DAY_OF_YEAR, dayOffset)
    c.set(Calendar.HOUR_OF_DAY, minOfDay / 60)
    c.set(Calendar.MINUTE, minOfDay % 60)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

fun nextOccurrence(minOfDay: Int, now: Long): Long {
    val t = dayAt(now, minOfDay, 0)
    return if (t <= now) dayAt(now, minOfDay, 1) else t
}

fun computeSleep(s: Settings, now: Long): SleepState {
    val dur = sleepDurMin(s) * 60_000L
    for (off in intArrayOf(-1, 0)) {
        val start = dayAt(now, s.bedMin, off)
        val end = start + dur
        if (now >= start && now < end) return SleepState(true, start, end)
    }
    val next = nextOccurrence(s.bedMin, now)
    val prevEnd = next - 86_400_000L + dur
    val from = if (prevEnd > now) next - 16 * 3_600_000L else prevEnd
    return SleepState(false, from, next)
}

// ---------------------------------------------------------------- días de la alarma

private val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Lunes = 0 … domingo = 6. */
fun dayIndexOf(ms: Long): Int {
    val dow = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)
    return (dow + 5) % 7
}

fun dayOn(mask: Int, idx: Int): Boolean = ((mask shr idx) and 1) == 1

/** Activa o desactiva un día, pero siempre deja al menos uno marcado. */
fun toggleDay(mask: Int, idx: Int): Int {
    val m = mask xor (1 shl idx)
    return if ((m and ALL_DAYS) == 0) mask else m
}

fun daysSummary(mask: Int): String {
    val m = mask and ALL_DAYS
    if (m == ALL_DAYS) return "Todos los días"
    if (m == 31) return "Lunes a viernes"
    if (m == 96) return "Sábado y domingo"
    return (0..6).filter { dayOn(m, it) }.joinToString(", ") { DAY_NAMES[it] }
        .replaceFirstChar { it.uppercase() }
}

/** Próxima vez que debe sonar la alarma de despertar, respetando los días elegidos. */
fun nextWake(s: Settings, now: Long): Long? {
    if ((s.wakeDays and ALL_DAYS) == 0) return null
    for (off in 0..8) {
        val t = dayAt(now, s.wakeMin, off)
        if (t > now && dayOn(s.wakeDays, dayIndexOf(t))) return t
    }
    return null
}

/** Próximo aviso para ir a dormir: solo en las noches previas a un día con alarma. */
fun nextBedReminder(s: Settings, now: Long): Long? {
    val m = (((s.bedMin - s.leadMin) % 1440) + 1440) % 1440
    for (off in 0..8) {
        val r = dayAt(now, m, off)
        if (r <= now) continue
        val bed = r + s.leadMin * 60_000L
        val wake = nextOccurrence(s.wakeMin, bed)
        if (dayOn(s.wakeDays, dayIndexOf(wake))) return r
    }
    return null
}

// ---------------------------------------------------------------- calculadora

/** Evalúa cuentas como "450000+35000", "1.500.000-200000", "(2*35000)+1200". Devuelve null si no es válida. */
fun evalExpr(raw: String): Double? {
    var s = raw.replace(" ", "").replace("×", "*").replace("÷", "/").replace("−", "-")
    s = s.replace(Regex("(?<=\\d)\\.(?=\\d{3}(?!\\d))"), "")
    s = s.replace(',', '.')
    if (s.isEmpty()) return null
    return try {
        ExprParser(s).parse()
    } catch (e: Exception) {
        null
    }
}


/** Evalúa una expresión ya limpia (solo números con punto decimal y + - * / paréntesis). */
fun evalPlain(s: String): Double? = try {
    ExprParser(s.replace(" ", "")).parse()
} catch (e: Exception) {
    null
}

private class ExprParser(val s: String) {
    var pos = 0

    fun parse(): Double? {
        val v = expr() ?: return null
        return if (pos == s.length && v.isFinite()) v else null
    }

    private fun expr(): Double? {
        var v = term() ?: return null
        while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
            val op = s[pos++]
            val r = term() ?: return null
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double? {
        var v = factor() ?: return null
        while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
            val op = s[pos++]
            val r = factor() ?: return null
            if (op == '/' && r == 0.0) return null
            v = if (op == '*') v * r else v / r
        }
        return v
    }

    private fun factor(): Double? {
        if (pos >= s.length) return null
        if (s[pos] == '-') {
            pos++
            val f = factor() ?: return null
            return -f
        }
        if (s[pos] == '(') {
            pos++
            val v = expr() ?: return null
            if (pos >= s.length || s[pos] != ')') return null
            pos++
            return v
        }
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        if (start == pos) return null
        return s.substring(start, pos).toDoubleOrNull()
    }
}
