package com.nioro.life

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingTest {
    @Test
    fun catalogoTieneMasDeCienEjerciciosSinRepetir() {
        assertTrue(EXERCISES.size > 100)
        assertEquals(EXERCISES.size, EXERCISES.map { it.id }.toSet().size)
        assertTrue(EXERCISES.all { it.muscle in MUSCLE_NAMES.indices && it.equip in EQUIP_NAMES.indices })
        // cada grupo muscular tiene ejercicios
        MUSCLE_NAMES.indices.forEach { m -> assertTrue(EXERCISES.any { it.muscle == m }) }
    }

    @Test
    fun rutinasListasUsanEjerciciosQueExisten() {
        for (r in PRESET_ROUTINES) for (it in r.items) {
            assertTrue("${r.name}: ${it.exId}", exerciseById(it.exId) != null)
        }
    }

    @Test
    fun unoRmYDiscos() {
        assertEquals(100.0, estimate1RM(100.0, 1), 1e-9)
        assertEquals(116.67, estimate1RM(100.0, 5), 0.01)
        assertEquals(0.0, estimate1RM(0.0, 5), 1e-9)

        val (plates, rest) = platesPerSide(100.0, 20.0)
        assertEquals(listOf(25.0, 15.0), plates)
        assertEquals(0.0, rest, 1e-9)
        val (p2, r2) = platesPerSide(61.0, 20.0)
        assertEquals(listOf(20.0), p2)
        assertEquals(1.0, r2, 1e-6)
        assertTrue(platesPerSide(15.0, 20.0).first.isEmpty())
    }

    @Test
    fun recordsVolumenYRacha() {
        val day = 86_400_000L
        val now = 1_760_000_000_000L
        val old = Workout(1, "A", now - 10 * day, now - 10 * day + 3_600_000, listOf(
            LogEntry("bench", listOf(SetLog(5, 80.0, true)), 90),
            LogEntry("pushup", listOf(SetLog(20, 0.0, true)), 60)
        ))
        val new = Workout(2, "B", now, now + 3_600_000, listOf(
            LogEntry("bench", listOf(SetLog(5, 85.0, true), SetLog(5, 60.0, true, kind = 1)), 90),
            LogEntry("pushup", listOf(SetLog(18, 0.0, true)), 60)
        ))
        // el calentamiento no suma volumen
        assertEquals(425.0, workoutVolume(new), 1e-9)
        val prs = newRecords(new, listOf(old))
        assertEquals(1, prs.size)
        assertTrue(prs[0].startsWith("Press de banca"))
        // lo de la última vez aparece como "anterior"
        assertEquals(85.0, lastSetsFor("bench", listOf(old, new)).first().kg, 1e-9)

        assertEquals(0, weekStreak(emptyList(), 3, now))
        val weekly = (0 until 3).map { w -> Workout(10L + w, "x", now - w * 7 * day, now - w * 7 * day + 1, emptyList()) }
        assertEquals(3, weekStreak(weekly, 1, now))
        assertEquals(8, weeklyVolumes(weekly, now).size)
    }

    @Test
    fun formatoDeKilos() {
        assertEquals("80", fmtKg(80.0))
        assertEquals("82,5", fmtKg(82.5))
        assertEquals("2,3", fmtKg(2.3))
        assertEquals("1,25", fmtKg(1.25))
        assertEquals("-2,5", fmtKg(-2.5))
        assertEquals("12,5 t", fmtVolume(12_480.0))
        assertEquals("950 kg", fmtVolume(950.0))
    }

    @Test
    fun rutinaPreparaSeriesConLoAnterior() {
        val rt = PRESET_ROUTINES.first { it.items.any { i -> i.exId == "bench" } }
        val hist = listOf(Workout(1, "A", 0, 1, listOf(LogEntry("bench", listOf(SetLog(6, 70.0, true)), 90))))
        val w = workoutFromRoutine(rt, hist, 100, 90)
        val bench = w.entries.first { it.exId == "bench" }
        assertEquals(rt.items.first { it.exId == "bench" }.sets, bench.sets.size)
        assertEquals(70.0, bench.sets[0].kg, 1e-9)
        // las reps son el objetivo de la rutina, no las de la última vez
        assertEquals(rt.items.first { it.exId == "bench" }.reps, bench.sets[0].reps)
        assertTrue(bench.sets.none { it.done })
        assertEquals(w.entries.size, w.entries.map { it.uid }.toSet().size)
    }
}
