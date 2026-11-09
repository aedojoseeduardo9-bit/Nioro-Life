package com.nioro.life

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.roundToInt

/** Tipos de serie, como en Hevy/Strong: normal, calentamiento, drop set y al fallo. */
val SET_KIND_LABEL = listOf("", "C", "D", "F")
val SET_KIND_NAME = listOf("Normal", "Calentamiento", "Drop set", "Al fallo")

/** Una serie. `reps` son segundos cuando el ejercicio es por tiempo. */
data class SetLog(val reps: Int, val kg: Double, val done: Boolean, val kind: Int = 0)

/** Un ejercicio dentro del entrenamiento. `linked` = superserie con el siguiente (sin descanso entre ellos). */
data class LogEntry(
    val exId: String,
    val sets: List<SetLog>,
    val restSec: Int,
    val note: String = "",
    val linked: Boolean = false,
    /** Identificador estable dentro del entrenamiento (para animar y editar sin confundir filas). */
    val uid: Long = 0L
)

data class Workout(
    val id: Long,
    val name: String,
    val start: Long,
    val end: Long,
    val entries: List<LogEntry>,
    val note: String = ""
)

data class TrainCfg(
    /** Días de entrenamiento por semana que te pones como meta. */
    val weeklyGoal: Int = 3,
    val defaultRest: Int = 90,
    val sound: Boolean = true,
    val vibrate: Boolean = true
)

data class BodyWeight(val date: Long, val kg: Double)

// ---------------------------------------------------------------- cálculos

/** 1RM estimado (fórmula de Epley). */
fun estimate1RM(kg: Double, reps: Int): Double = when {
    reps <= 0 || kg <= 0.0 -> 0.0
    reps == 1 -> kg
    else -> kg * (1.0 + reps / 30.0)
}

/** Porcentajes del 1RM para cada número de repeticiones (tabla típica de fuerza). */
fun repMaxTable(oneRm: Double): List<Pair<Int, Double>> =
    listOf(1 to 1.0, 2 to 0.95, 3 to 0.93, 4 to 0.90, 5 to 0.87, 6 to 0.85, 8 to 0.80, 10 to 0.75, 12 to 0.70, 15 to 0.65)
        .map { (r, pct) -> r to oneRm * pct }

/** Discos que van a cada lado de la barra para llegar a `target` kg. Devuelve (discos, kg que sobran). */
fun platesPerSide(
    target: Double,
    bar: Double = 20.0,
    available: List<Double> = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
): Pair<List<Double>, Double> {
    var side = (target - bar) / 2.0
    if (side <= 0) return emptyList<Double>() to 0.0
    val out = mutableListOf<Double>()
    for (p in available.sortedDescending()) {
        while (side + 1e-9 >= p) {
            out += p
            side -= p
        }
    }
    return out to (side * 2).let { if (it < 1e-6) 0.0 else it }
}

fun isWeighted(type: Int) = type == EX_WEIGHT

fun setVolume(s: SetLog): Double = if (s.done && s.kind != 1) s.kg * s.reps else 0.0

fun workoutVolume(w: Workout): Double = w.entries.sumOf { e -> e.sets.sumOf { setVolume(it) } }

fun workoutDoneSets(w: Workout): Int = w.entries.sumOf { e -> e.sets.count { it.done } }

/** 82.5 → "82,5", 80.0 → "80", 2.3 → "2,3" (sin ceros de más por errores de redondeo). */
fun fmtKg(v: Double): String {
    val cents = (v * 100).roundToInt()
    val neg = cents < 0
    val a = kotlin.math.abs(cents)
    val ent = a / 100
    val dec = a % 100
    val txt = when {
        dec == 0 -> "$ent"
        dec % 10 == 0 -> "$ent,${dec / 10}"
        else -> "$ent,%02d".format(dec)
    }
    return if (neg) "-$txt" else txt
}

fun fmtVolume(v: Double): String =
    if (v >= 10_000) "${fmtKg(Math.round(v / 100.0) / 10.0)} t" else "${fmtKg(Math.round(v).toDouble())} kg"

fun fmtClock(sec: Long): String {
    val s = maxOf(0L, sec)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%02d:%02d".format(m, ss)
}

/** Mejor 1RM estimado de cada ejercicio en el historial. */
fun bestOneRm(history: List<Workout>): Map<String, Double> {
    val best = HashMap<String, Double>()
    for (w in history) for (e in w.entries) for (s in e.sets) {
        if (!s.done || s.kind == 1) continue
        val v = estimate1RM(s.kg, s.reps)
        if (v > (best[e.exId] ?: 0.0)) best[e.exId] = v
    }
    return best
}

/** Mejor marca sin peso: más repeticiones (o más segundos) en una serie. */
fun bestReps(history: List<Workout>): Map<String, Int> {
    val best = HashMap<String, Int>()
    for (w in history) for (e in w.entries) for (s in e.sets) {
        if (!s.done) continue
        if (s.reps > (best[e.exId] ?: 0)) best[e.exId] = s.reps
    }
    return best
}

/** Récords nuevos que logra `w` comparado con el historial anterior. */
fun newRecords(w: Workout, previous: List<Workout>, custom: List<Exercise> = emptyList()): List<String> {
    val oneRm = bestOneRm(previous)
    val reps = bestReps(previous)
    val out = mutableListOf<String>()
    for (e in w.entries) {
        val ex = exerciseById(e.exId, custom) ?: continue
        val done = e.sets.filter { it.done && it.kind != 1 }
        if (done.isEmpty()) continue
        if (isWeighted(ex.type)) {
            val top = done.maxOf { estimate1RM(it.kg, it.reps) }
            val old = oneRm[e.exId]
            if (top > 0 && old != null && top > old + 1e-6) out += "${ex.name}: 1RM ≈ ${fmtKg(top)} kg"
        } else {
            val top = done.maxOf { it.reps }
            val old = reps[e.exId]
            if (old != null && top > old) {
                out += "${ex.name}: " + if (ex.type == EX_TIME) "${top} s" else "$top reps"
            }
        }
    }
    return out
}

/** Las series de la última vez que hiciste el ejercicio (se muestran como "Anterior"). */
fun lastSetsFor(exId: String, history: List<Workout>): List<SetLog> =
    history.sortedByDescending { it.start }
        .firstNotNullOfOrNull { w -> w.entries.firstOrNull { it.exId == exId && it.sets.any { s -> s.done } } }
        ?.sets?.filter { it.done } ?: emptyList()

private fun weekStart(ms: Long): Long = Calendar.getInstance().apply {
    timeInMillis = ms
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    val dow = (get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunes = 0
    add(Calendar.DAY_OF_YEAR, -dow)
}.timeInMillis

private fun dayKey(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }
    .let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }

/** Días distintos con entrenamiento en la semana actual (lunes a domingo). */
fun daysThisWeek(history: List<Workout>, now: Long): Int {
    val ws = weekStart(now)
    return history.filter { it.start >= ws }.map { dayKey(it.start) }.toSet().size
}

/** Semanas seguidas cumpliendo la meta. La semana actual cuenta solo si ya la cumpliste. */
fun weekStreak(history: List<Workout>, goal: Int, now: Long): Int {
    if (history.isEmpty() || goal <= 0) return 0
    val byWeek = history.groupBy { weekStart(it.start) }.mapValues { (_, ws) -> ws.map { dayKey(it.start) }.toSet().size }
    var wk = weekStart(now)
    var streak = 0
    if ((byWeek[wk] ?: 0) >= goal) streak++
    while (true) {
        wk = weekStart(wk - 3 * 86_400_000L)
        if ((byWeek[wk] ?: 0) >= goal) streak++ else break
    }
    return streak
}

/** Volumen de cada una de las últimas `n` semanas (la última es la actual). */
fun weeklyVolumes(history: List<Workout>, now: Long, n: Int = 8): List<Double> {
    val starts = mutableListOf(weekStart(now))
    repeat(n - 1) { starts.add(0, weekStart(starts.first() - 3 * 86_400_000L)) }
    return starts.map { s -> history.filter { weekStart(it.start) == s }.sumOf { workoutVolume(it) } }
}

/** Series hechas por grupo muscular en los últimos 7 días (para el mapa de músculos). */
fun muscleSets(history: List<Workout>, now: Long, custom: List<Exercise> = emptyList()): IntArray {
    val out = IntArray(MUSCLE_NAMES.size)
    val from = now - 7 * 86_400_000L
    for (w in history) {
        if (w.start < from) continue
        for (e in w.entries) {
            val ex = exerciseById(e.exId, custom) ?: continue
            out[ex.muscle] += e.sets.count { it.done }
        }
    }
    return out
}

/** Crea un entrenamiento nuevo a partir de una rutina, con las series ya preparadas. */
fun workoutFromRoutine(rt: Routine?, history: List<Workout>, now: Long, defaultRest: Int): Workout {
    if (rt == null) return Workout(now, "Entrenamiento libre", now, 0L, emptyList())
    val entries = rt.items.mapIndexed { idx, it ->
        val prev = lastSetsFor(it.exId, history)
        LogEntry(
            exId = it.exId,
            sets = List(it.sets) { i ->
                val p = prev.getOrNull(i) ?: prev.lastOrNull()
                // reps (o segundos) = el objetivo de la rutina; el peso = el que usaste la última vez
                SetLog(reps = it.reps, kg = p?.kg ?: 0.0, done = false)
            },
            restSec = if (it.restSec > 0) it.restSec else defaultRest,
            uid = idx + 1L
        )
    }
    return Workout(now, rt.name, now, 0L, entries)
}

/** Guarda lo que hiciste como rutina nueva (botón "Guardar como rutina"). */
fun routineFromWorkout(w: Workout, name: String): Routine = Routine(
    id = System.currentTimeMillis(),
    name = name,
    items = w.entries.map { e ->
        RoutineItem(e.exId, maxOf(1, e.sets.size), e.sets.firstOrNull()?.reps ?: 10, e.restSec)
    }
)

// ---------------------------------------------------------------- guardado

object TrainStore {
    private fun prefs(c: Context) = c.getSharedPreferences("nioro_train", Context.MODE_PRIVATE)

    private fun setToJson(s: SetLog) = JSONObject().put("r", s.reps).put("kg", s.kg).put("d", s.done).put("k", s.kind)
    private fun setFromJson(o: JSONObject) = SetLog(o.optInt("r"), o.optDouble("kg", 0.0), o.optBoolean("d"), o.optInt("k"))

    private fun workoutToJson(w: Workout): JSONObject {
        val entries = JSONArray()
        for (e in w.entries) {
            val sets = JSONArray()
            e.sets.forEach { sets.put(setToJson(it)) }
            entries.put(
                JSONObject().put("ex", e.exId).put("rest", e.restSec).put("note", e.note)
                    .put("link", e.linked).put("uid", e.uid).put("sets", sets)
            )
        }
        return JSONObject().put("id", w.id).put("name", w.name).put("start", w.start).put("end", w.end)
            .put("note", w.note).put("entries", entries)
    }

    private fun workoutFromJson(o: JSONObject): Workout {
        val ea = o.optJSONArray("entries") ?: JSONArray()
        val entries = (0 until ea.length()).map { i ->
            val e = ea.getJSONObject(i)
            val sa = e.optJSONArray("sets") ?: JSONArray()
            LogEntry(
                exId = e.optString("ex"),
                sets = (0 until sa.length()).map { setFromJson(sa.getJSONObject(it)) },
                restSec = e.optInt("rest", 90),
                note = e.optString("note", ""),
                linked = e.optBoolean("link", false),
                uid = e.optLong("uid", (i + 1).toLong())
            )
        }
        return Workout(
            o.optLong("id"), o.optString("name", "Entrenamiento"), o.optLong("start"), o.optLong("end"),
            entries, o.optString("note", "")
        )
    }

    fun loadHistory(c: Context): List<Workout> = try {
        val a = JSONArray(prefs(c).getString("history", "[]") ?: "[]")
        (0 until a.length()).map { workoutFromJson(a.getJSONObject(it)) }
    } catch (e: Exception) { emptyList() }

    fun saveHistory(c: Context, list: List<Workout>) {
        val a = JSONArray()
        list.forEach { a.put(workoutToJson(it)) }
        prefs(c).edit().putString("history", a.toString()).apply()
    }

    fun loadActive(c: Context): Workout? = try {
        prefs(c).getString("active", null)?.let { workoutFromJson(JSONObject(it)) }
    } catch (e: Exception) { null }

    fun saveActive(c: Context, w: Workout?) {
        val ed = prefs(c).edit()
        if (w == null) ed.remove("active") else ed.putString("active", workoutToJson(w).toString())
        ed.apply()
    }

    fun loadRoutines(c: Context): List<Routine> = try {
        val a = JSONArray(prefs(c).getString("routines", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val ia = o.optJSONArray("items") ?: JSONArray()
            Routine(
                o.optLong("id"), o.optString("name"),
                (0 until ia.length()).map { j ->
                    val it = ia.getJSONObject(j)
                    RoutineItem(it.optString("ex"), it.optInt("sets", 3), it.optInt("reps", 10), it.optInt("rest", 90))
                },
                desc = o.optString("desc", "")
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveRoutines(c: Context, list: List<Routine>) {
        val a = JSONArray()
        for (r in list) {
            val ia = JSONArray()
            r.items.forEach { ia.put(JSONObject().put("ex", it.exId).put("sets", it.sets).put("reps", it.reps).put("rest", it.restSec)) }
            a.put(JSONObject().put("id", r.id).put("name", r.name).put("desc", r.desc).put("items", ia))
        }
        prefs(c).edit().putString("routines", a.toString()).apply()
    }

    fun loadCustom(c: Context): List<Exercise> = try {
        val a = JSONArray(prefs(c).getString("custom", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Exercise(o.optString("id"), o.optString("name"), o.optInt("m"), o.optInt("e"), o.optInt("t"), o.optString("tip"), true)
        }
    } catch (e: Exception) { emptyList() }

    fun saveCustom(c: Context, list: List<Exercise>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("m", it.muscle).put("e", it.equip).put("t", it.type).put("tip", it.tip))
        }
        prefs(c).edit().putString("custom", a.toString()).apply()
    }

    fun loadRest(c: Context): Pair<Long, Int> = prefs(c).let { it.getLong("restEnd", 0L) to it.getInt("restTotal", 0) }
    fun saveRest(c: Context, end: Long, total: Int) {
        prefs(c).edit().putLong("restEnd", end).putInt("restTotal", total).apply()
    }

    fun loadFavs(c: Context): Set<String> = prefs(c).getStringSet("favs", emptySet())?.toSet() ?: emptySet()
    fun saveFavs(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("favs", s).apply() }

    fun loadCfg(c: Context): TrainCfg {
        val p = prefs(c)
        return TrainCfg(p.getInt("goal", 3), p.getInt("rest", 90), p.getBoolean("sound", true), p.getBoolean("vib", true))
    }

    fun saveCfg(c: Context, t: TrainCfg) {
        prefs(c).edit().putInt("goal", t.weeklyGoal).putInt("rest", t.defaultRest)
            .putBoolean("sound", t.sound).putBoolean("vib", t.vibrate).apply()
    }

    fun loadBody(c: Context): List<BodyWeight> = try {
        val a = JSONArray(prefs(c).getString("body", "[]") ?: "[]")
        (0 until a.length()).map { val o = a.getJSONObject(it); BodyWeight(o.optLong("d"), o.optDouble("kg", 0.0)) }
    } catch (e: Exception) { emptyList() }

    fun saveBody(c: Context, list: List<BodyWeight>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("d", it.date).put("kg", it.kg)) }
        prefs(c).edit().putString("body", a.toString()).apply()
    }
}
