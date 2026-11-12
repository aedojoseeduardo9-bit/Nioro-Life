package com.nioro.life

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Saludo según la hora del día (5–11 días, 12–19 tardes, resto noches). */
fun greeting(hour: Int): String = when (hour) {
    in 5..11 -> "¡Hola, buenos días!"
    in 12..19 -> "¡Hola, buenas tardes!"
    else -> "¡Hola, buenas noches!"
}

private const val BUBBLE_DP = 66

/**
 * Burbuja flotante del asistente: flota sobre todas las pantallas de Nioro Life, se puede arrastrar
 * y se pega al borde más cercano. Al tocarla se aprieta, rebota y abre el asistente desde donde está.
 */
@Composable
fun AssistantBubble(visible: Boolean, onOpen: (TransformOrigin) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("nioro_bubble", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    // aparece con un "pop" y se esconde encogiéndose
    val pop = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(250)
            pop.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessLow))
        } else pop.animateTo(0f, tween(160))
    }
    val press = remember { Animatable(1f) }
    val t by rememberInfiniteTransition(label = "bubble").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "bt"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val size = with(density) { BUBBLE_DP.dp.toPx() }
        val margin = with(density) { 10.dp.toPx() }
        val minY = with(density) { 90.dp.toPx() }
        val maxX = (w - size - margin).coerceAtLeast(margin)
        val maxY = (h - size - with(density) { 150.dp.toPx() }).coerceAtLeast(minY)
        val x = remember { Animatable(if (prefs.getBoolean("left", false)) margin else maxX) }
        val y = remember { Animatable((minY + prefs.getFloat("y", 0.55f) * (maxY - minY)).coerceIn(minY, maxY)) }

        if (pop.value <= 0.01f && !visible) return@BoxWithConstraints

        fun snapToEdge() = scope.launch {
            val left = x.value + size / 2 < w / 2
            prefs.edit().putBoolean("left", left).putFloat("y", ((y.value - minY) / (maxY - minY)).coerceIn(0f, 1f)).apply()
            x.animateTo(if (left) margin else maxX, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow))
        }

        Box(
            Modifier
                .offset { IntOffset(x.value.roundToInt(), y.value.roundToInt()) }
                .size(BUBBLE_DP.dp)
                .graphicsLayer {
                    val s = pop.value * press.value
                    scaleX = s
                    scaleY = s
                    alpha = pop.value.coerceIn(0f, 1f)
                    // flota suavemente
                    translationY = kotlin.math.sin(t * 2 * Math.PI).toFloat() * 3.dp.toPx()
                }
                .semantics { contentDescription = "Nioro IA" }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(onDragEnd = { snapToEdge() }, onDragCancel = { snapToEdge() }) { change, drag ->
                        change.consume()
                        scope.launch {
                            x.snapTo((x.value + drag.x).coerceIn(margin, maxX))
                            y.snapTo((y.value + drag.y).coerceIn(minY, maxY))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            press.animateTo(0.8f, tween(90))
                            val origin = TransformOrigin(
                                ((x.value + size / 2) / w).coerceIn(0f, 1f),
                                ((y.value + size / 2) / h).coerceIn(0f, 1f)
                            )
                            onOpen(origin)
                            press.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                        }
                    })
                }
        ) {
            BubbleOrb(t)
        }
    }
}

/** La esfera de Nioro dentro de una pompa de jabón con borde de colores que gira. */
@Composable
private fun BubbleOrb(t: Float) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            // sombra suave
            drawCircle(Brush.radialGradient(listOf(p.shadow.copy(alpha = 0.45f), Color.Transparent), center + Offset(0f, r * 0.18f), r * 1.15f), r * 1.15f, center + Offset(0f, r * 0.18f))
        }
        NioroOrb(OrbMode.Idle, 0f, Modifier.fillMaxSize().padding(2.dp))
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2 * 0.74f
            // borde iridiscente de pompa de jabón
            rotate(t * 360f) {
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            Color(0x99FF8AD8), Color(0x998AF0FF), Color(0x99FFF08A), Color(0x99A98BFF), Color(0x99FF8AD8)
                        ), center
                    ),
                    r, center, style = Stroke(size.minDimension * 0.045f)
                )
            }
            // brillo de la pompa
            drawCircle(Color.White.copy(alpha = 0.55f), r * 0.16f, Offset(center.x - r * 0.42f, center.y - r * 0.46f))
            drawCircle(Color.White.copy(alpha = 0.25f), r * 0.08f, Offset(center.x - r * 0.2f, center.y - r * 0.62f))
        }
    }
}
