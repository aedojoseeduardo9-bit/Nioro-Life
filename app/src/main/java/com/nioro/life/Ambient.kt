package com.nioro.life

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Fondo animado según el tema: estrellas (noche/día), pétalos (rosa), brasas (rojo), nebulosa (morado). */
@Composable
fun Ambient() {
    when (LocalPalette.current.id) {
        2 -> PetalsAmbient()
        3 -> EmbersAmbient()
        4 -> NebulaAmbient()
        else -> Stars()
    }
}

private val TAU = (2 * PI).toFloat()

/** Reloj de 0 a 1 que se repite sin saltos (todo se calcula con ciclos enteros). */
@Composable
private fun loopClock(millis: Int): State<Float> {
    val tr = rememberInfiniteTransition(label = "ambient")
    return tr.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(millis, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )
}

private class Mote(
    val x: Float, val size: Float, val cycles: Int, val off: Float,
    val sway: Float, val freq: Int, val phase: Float, val tint: Float
)

private fun motes(seed: Long, n: Int, size: ClosedFloatingPointRange<Float>, cycles: IntRange, sway: Float): List<Mote> {
    val r = java.util.Random(seed)
    return List(n) {
        Mote(
            x = r.nextFloat(),
            size = size.start + r.nextFloat() * (size.endInclusive - size.start),
            cycles = cycles.first + r.nextInt(cycles.last - cycles.first + 1),
            off = r.nextFloat(),
            sway = sway * (0.4f + r.nextFloat() * 0.6f),
            freq = 1 + r.nextInt(3),
            phase = r.nextFloat() * TAU,
            tint = r.nextFloat()
        )
    }
}

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.55f)
    cubicTo(cx - s * 1.1f, cy - s * 0.05f, cx - s * 0.6f, cy - s * 0.95f, cx, cy - s * 0.35f)
    cubicTo(cx + s * 0.6f, cy - s * 0.95f, cx + s * 1.1f, cy - s * 0.05f, cx, cy + s * 0.55f)
    close()
}

/** Rosa: corazones y pétalos que suben despacio, balanceándose, con un brillo suave que late. */
@Composable
private fun PetalsAmbient() {
    val p = LocalPalette.current
    val t by loopClock(48_000)
    val parts = remember { motes(11L, 18, 7f..20f, 2..4, 26f) }
    Canvas(Modifier.fillMaxSize()) {
        val pulse = 0.5f + 0.5f * sin(t * TAU * 6)
        drawRect(
            Brush.radialGradient(
                listOf(p.accent.copy(alpha = 0.10f + 0.08f * pulse), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 1.1f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(p.title.copy(alpha = 0.05f + 0.05f * (1f - pulse)), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.75f), radius = size.width
            )
        )
        for (m in parts) {
            val ph = (t * m.cycles + m.off) % 1f
            val y = size.height * (1.08f - 1.16f * ph)
            val x = m.x * size.width + sin(t * TAU * m.freq + m.phase) * m.sway.dp.toPx()
            val a = sin(ph * PI.toFloat()).coerceIn(0f, 1f) * (0.30f + 0.30f * m.tint)
            val color = if (m.tint > 0.5f) p.title else p.accent
            drawPath(heartPath(x, y, m.size.dp.toPx()), color.copy(alpha = a))
        }
    }
}

/** Rojo: brasas que suben titilando y un resplandor de fuego abajo que late. */
@Composable
private fun EmbersAmbient() {
    val p = LocalPalette.current
    val t by loopClock(36_000)
    val sparks = remember { motes(23L, 34, 1.4f..4.2f, 4..9, 18f) }
    val hot = Color(0xFFFFD27A)
    val warm = Color(0xFFFF7A3D)
    Canvas(Modifier.fillMaxSize()) {
        val beat = 0.5f + 0.5f * sin(t * TAU * 8)
        drawRect(
            Brush.radialGradient(
                listOf(warm.copy(alpha = 0.14f + 0.16f * beat), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 1.06f), radius = size.height * 0.75f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(p.accent.copy(alpha = 0.06f + 0.05f * beat), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.05f), radius = size.width
            )
        )
        for (m in sparks) {
            val ph = (t * m.cycles + m.off) % 1f
            val y = size.height * (1.04f - 1.1f * ph)
            val x = m.x * size.width + sin(t * TAU * m.freq + m.phase) * m.sway.dp.toPx()
            val flicker = 0.55f + 0.45f * sin(t * TAU * (6 + m.freq * 3) + m.phase)
            val fade = sin(ph * PI.toFloat()).coerceIn(0f, 1f)
            val r = m.size.dp.toPx() * (1f - 0.55f * ph)
            val c = if (m.tint > 0.45f) hot else warm
            drawCircle(c.copy(alpha = 0.18f * fade * flicker), r * 2.6f, Offset(x, y))
            drawCircle(c.copy(alpha = 0.85f * fade * flicker), r, Offset(x, y))
        }
    }
}

/** Morado: nebulosa de luz que se mueve despacio, luciérnagas y estrellas titilando. */
@Composable
private fun NebulaAmbient() {
    val p = LocalPalette.current
    val t by loopClock(60_000)
    val orbs = remember { motes(37L, 14, 1.6f..3.6f, 1..2, 22f) }
    Stars()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFB06BFF).copy(alpha = 0.26f), Color.Transparent),
                center = Offset(w * (0.30f + 0.20f * sin(t * TAU)), h * (0.22f + 0.10f * cos(t * TAU))),
                radius = w * 0.95f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFF6A7BFF).copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * (0.78f + 0.14f * cos(t * TAU)), h * (0.55f + 0.12f * sin(t * TAU * 2))),
                radius = w * 0.85f
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(Color(0xFFFF6FD8).copy(alpha = 0.17f), Color.Transparent),
                center = Offset(w * (0.40f + 0.22f * sin(t * TAU * 2 + 1f)), h * (0.92f - 0.08f * cos(t * TAU))),
                radius = w * 0.9f
            )
        )
        for (m in orbs) {
            val ph = (t * m.cycles + m.off) % 1f
            val y = h * (1.02f - 1.04f * ph)
            val x = m.x * w + sin(t * TAU * m.freq + m.phase) * m.sway.dp.toPx()
            val tw = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * TAU * (3 + m.freq) + m.phase))
            val fade = sin(ph * PI.toFloat()).coerceIn(0f, 1f)
            drawCircle(p.accent.copy(alpha = 0.16f * fade * tw), m.size.dp.toPx() * 3f, Offset(x, y))
            drawCircle(p.title.copy(alpha = 0.8f * fade * tw), m.size.dp.toPx(), Offset(x, y))
        }
    }
}
