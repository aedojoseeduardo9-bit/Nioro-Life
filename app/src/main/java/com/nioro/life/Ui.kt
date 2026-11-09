package com.nioro.life

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tarjeta de vidrio líquido: degradado translúcido, borde con brillo y luz superior. */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    radius: Dp = 30.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(radius)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val sc by animateFloatAsState(
        if (pressed && onClick != null) 0.955f else 1f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = sc
                scaleY = sc
            }
            .shadow(14.dp, shape, clip = false, ambientColor = p.shadow, spotColor = p.shadow)
            .clip(shape)
            .background(Brush.linearGradient(p.glass))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(p.glassHighlight, Color.Transparent),
                        center = Offset(size.width * 0.25f, 0f),
                        radius = size.width * 0.9f
                    )
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)), shape)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = src, indication = null) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                else Modifier
            ),
        content = content
    )
}

/**
 * Entrada animada: el elemento aparece subiendo y aclarándose, con un pequeño retraso
 * según su posición (efecto "cascada" al abrir una pantalla).
 */
@Composable
fun Modifier.rise(index: Int = 0): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // tope al retraso: en listas largas los de abajo no deben quedar invisibles casi un segundo
        kotlinx.coroutines.delay(45L * index.coerceIn(0, 6))
        a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        val v = a.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 36.dp.toPx()
        val sc = 0.94f + 0.06f * v
        scaleX = sc
        scaleY = sc
    }
}

@Composable
fun GlowText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.ExtraBold,
    color: Color = Color.Unspecified,
    glow: Boolean = true
) {
    val p = LocalPalette.current
    Text(
        text = text,
        modifier = modifier,
        color = if (color == Color.Unspecified) p.title else color,
        fontSize = size,
        fontWeight = weight,
        style = TextStyle(shadow = if (glow) Shadow(p.glow, Offset.Zero, 24f) else null)
    )
}

@Composable
fun IconBadge(content: @Composable BoxScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .size(54.dp)
            .clip(shape)
            .background(Brush.radialGradient(p.badge, center = Offset(40f, 30f), radius = 160f))
            .border(1.dp, p.badgeBorder, shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (selected) p.accentSoft else p.chipBg, tween(220), label = "chipBg")
    val bd by animateColorAsState(if (selected) p.accent else p.chipBorder, tween(220), label = "chipBd")
    val sc by animateFloatAsState(if (selected) 1.04f else 1f, spring(dampingRatio = 0.5f), label = "chipSc")
    Box(
        Modifier.graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(shape)
            .background(bg)
            .border(1.dp, bd, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(label, color = p.title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DialogButton(label: String, main: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "btn")
    Box(
        modifier.height(52.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape)
            .background(
                if (main) Brush.verticalGradient(p.btnMain)
                else Brush.verticalGradient(listOf(p.chipBg, p.chipBg))
            )
            .border(1.dp, if (main) p.btnMainBorder else p.chipBorder, shape)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (main) p.onBtnMain else p.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Glass(Modifier.size(46.dp), radius = 16.dp, onClick = onBack) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BackIcon(color = p.title) }
        }
        Spacer(Modifier.width(12.dp))
        GlowText(title, 25.sp)
    }
}

@Composable
fun fieldColors(): TextFieldColors {
    val p = LocalPalette.current
    return TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = p.fieldLine,
        unfocusedIndicatorColor = p.fieldLineOff,
        cursorColor = p.accent,
        focusedTextColor = p.fieldText,
        unfocusedTextColor = p.fieldText,
        focusedPlaceholderColor = p.placeholder,
        unfocusedPlaceholderColor = p.placeholder
    )
}

/** Luna creciente igual a la del icono, dentro de una caja 100x100 escalada. */
fun moonPath(s: Float, ox: Float, oy: Float): Path = Path().apply {
    moveTo(ox + 62 * s, oy + 8 * s)
    cubicTo(ox + 34 * s, oy + 12 * s, ox + 14 * s, oy + 33 * s, ox + 14 * s, oy + 58 * s)
    cubicTo(ox + 14 * s, oy + 83 * s, ox + 34 * s, oy + 100 * s, ox + 58 * s, oy + 100 * s)
    cubicTo(ox + 74 * s, oy + 100 * s, ox + 87 * s, oy + 92 * s, ox + 94 * s, oy + 79 * s)
    cubicTo(ox + 67 * s, oy + 85 * s, ox + 44 * s, oy + 70 * s, ox + 44 * s, oy + 46 * s)
    cubicTo(ox + 44 * s, oy + 32 * s, ox + 50 * s, oy + 20 * s, ox + 62 * s, oy + 8 * s)
    close()
}

/** Dibuja la luna centrada en el área disponible, ocupando `fraction` del ancho. */
fun DrawScope.drawMoon(p: Palette, fraction: Float) {
    val ms = size.width * fraction / 100f
    val ox = (size.width - 100 * ms) / 2
    val oy = (size.height - 100 * ms) / 2
    val mp = moonPath(ms, ox, oy)
    val glowColor = if (p.light) p.accent else p.title
    drawPath(mp, glowColor.copy(alpha = 0.10f), style = Stroke(width = 28f))
    drawPath(mp, glowColor.copy(alpha = 0.14f), style = Stroke(width = 14f))
    drawPath(
        mp,
        Brush.radialGradient(
            p.moon,
            center = Offset(size.width * 0.4f, size.height * 0.35f),
            radius = size.width * 0.6f
        ),
        alpha = p.moonAlpha
    )
    if (p.moonEdge.alpha > 0f) drawPath(mp, p.moonEdge, style = Stroke(width = 3f))
}

@Composable
fun Stars() {
    val p = LocalPalette.current
    val stars = remember {
        val r = java.util.Random(7)
        List(42) { Triple(r.nextFloat(), r.nextFloat() * 0.85f, r.nextFloat() * 1.6f + 0.8f) }
    }
    val tr = rememberInfiniteTransition(label = "stars")
    val a by tr.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(3200), RepeatMode.Reverse),
        label = "twinkle"
    )
    Canvas(Modifier.fillMaxSize()) {
        for ((x, y, r) in stars) {
            val alpha = (a * (0.4f + r / 3f)).coerceAtMost(1f)
            drawCircle(p.star.copy(alpha = alpha), r.dp.toPx(), Offset(x * size.width, y * size.height))
        }
    }
}

@Composable
fun ClockIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        val c = Offset(w / 2, w * 0.56f)
        drawCircle(color, radius = w * 0.34f, center = c, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, c, Offset(c.x, c.y - w * 0.2f), sw, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + w * 0.13f, c.y + w * 0.09f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.12f, w * 0.22f), Offset(w * 0.26f, w * 0.1f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.88f, w * 0.22f), Offset(w * 0.74f, w * 0.1f), sw, StrokeCap.Round)
    }
}

@Composable
fun NoteIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        val p = Path().apply {
            moveTo(w * 0.22f, w * 0.1f)
            lineTo(w * 0.62f, w * 0.1f)
            lineTo(w * 0.8f, w * 0.28f)
            lineTo(w * 0.8f, w * 0.9f)
            lineTo(w * 0.22f, w * 0.9f)
            close()
        }
        drawPath(p, color, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.62f, w * 0.1f), Offset(w * 0.62f, w * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.62f, w * 0.28f), Offset(w * 0.8f, w * 0.28f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, w * 0.52f), Offset(w * 0.68f, w * 0.52f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, w * 0.7f), Offset(w * 0.56f, w * 0.7f), sw, StrokeCap.Round)
    }
}


/** Lista con casillas marcadas. */
@Composable
fun CheckIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        // casilla 1 con visto
        drawRoundRect(color, Offset(w * 0.1f, w * 0.14f), androidx.compose.ui.geometry.Size(w * 0.24f, w * 0.24f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.05f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.16f, w * 0.27f), Offset(w * 0.21f, w * 0.33f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.21f, w * 0.33f), Offset(w * 0.3f, w * 0.19f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.48f, w * 0.26f), Offset(w * 0.9f, w * 0.26f), sw, StrokeCap.Round)
        // casilla 2
        drawRoundRect(color, Offset(w * 0.1f, w * 0.58f), androidx.compose.ui.geometry.Size(w * 0.24f, w * 0.24f),
            androidx.compose.ui.geometry.CornerRadius(w * 0.05f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.48f, w * 0.7f), Offset(w * 0.8f, w * 0.7f), sw, StrokeCap.Round)
    }
}

/** Moneda con símbolo de pesos, dibujada con trazos. */
@Composable
fun CoinIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        val c = Offset(w / 2, w / 2)
        drawCircle(color, radius = w * 0.4f, center = c, style = Stroke(sw, cap = StrokeCap.Round))
        val s = Path().apply {
            moveTo(w * 0.6f, w * 0.38f)
            cubicTo(w * 0.58f, w * 0.3f, w * 0.4f, w * 0.3f, w * 0.4f, w * 0.42f)
            cubicTo(w * 0.4f, w * 0.52f, w * 0.6f, w * 0.48f, w * 0.6f, w * 0.6f)
            cubicTo(w * 0.6f, w * 0.72f, w * 0.42f, w * 0.7f, w * 0.4f, w * 0.62f)
        }
        drawPath(s, color, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, w * 0.23f), Offset(w * 0.5f, w * 0.77f), sw * 0.8f, StrokeCap.Round)
    }
}

@Composable
fun MenuIcon(dp: Dp = 22.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.1f
        drawLine(color, Offset(w * 0.15f, w * 0.3f), Offset(w * 0.85f, w * 0.3f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, w * 0.5f), Offset(w * 0.85f, w * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.15f, w * 0.7f), Offset(w * 0.6f, w * 0.7f), sw, StrokeCap.Round)
    }
}

@Composable
fun BackIcon(dp: Dp = 22.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.1f
        drawLine(color, Offset(w * 0.65f, w * 0.18f), Offset(w * 0.3f, w * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.65f, w * 0.82f), sw, StrokeCap.Round)
    }
}

@Composable
fun ForwardIcon(dp: Dp = 22.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.1f
        drawLine(color, Offset(w * 0.35f, w * 0.18f), Offset(w * 0.7f, w * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.7f, w * 0.5f), Offset(w * 0.35f, w * 0.82f), sw, StrokeCap.Round)
    }
}

@Composable
fun PlusIcon(dp: Dp = 32.dp, color: Color = LocalPalette.current.fabIcon) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.1f
        drawLine(color, Offset(w * 0.5f, w * 0.2f), Offset(w * 0.5f, w * 0.8f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, w * 0.5f), Offset(w * 0.8f, w * 0.5f), sw, StrokeCap.Round)
    }
}

@Composable
fun CloseIcon(dp: Dp = 14.dp, color: Color = LocalPalette.current.muted) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.14f
        drawLine(color, Offset(w * 0.15f, w * 0.15f), Offset(w * 0.85f, w * 0.85f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, w * 0.15f), Offset(w * 0.15f, w * 0.85f), sw, StrokeCap.Round)
    }
}

/** Ampolleta para la sección Casa. */
@Composable
fun BulbIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        val bulb = Path().apply {
            moveTo(w * 0.38f, w * 0.68f)
            cubicTo(w * 0.38f, w * 0.56f, w * 0.2f, w * 0.5f, w * 0.2f, w * 0.36f)
            cubicTo(w * 0.2f, w * 0.18f, w * 0.34f, w * 0.08f, w * 0.5f, w * 0.08f)
            cubicTo(w * 0.66f, w * 0.08f, w * 0.8f, w * 0.18f, w * 0.8f, w * 0.36f)
            cubicTo(w * 0.8f, w * 0.5f, w * 0.62f, w * 0.56f, w * 0.62f, w * 0.68f)
            close()
        }
        drawPath(bulb, color, style = Stroke(sw, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.4f, w * 0.79f), Offset(w * 0.6f, w * 0.79f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.43f, w * 0.9f), Offset(w * 0.57f, w * 0.9f), sw, StrokeCap.Round)
    }
}

/** Pesa (mancuerna) para la sección de entrenamiento. */
@Composable
fun DumbbellIcon(dp: Dp = 30.dp, color: Color = LocalPalette.current.title) {
    Canvas(Modifier.size(dp)) {
        val w = size.minDimension
        val sw = w * 0.075f
        val cr = androidx.compose.ui.geometry.CornerRadius(w * 0.04f)
        drawLine(color, Offset(w * 0.3f, w * 0.5f), Offset(w * 0.7f, w * 0.5f), sw, StrokeCap.Round)
        for (x in listOf(w * 0.17f, w * 0.71f)) {
            drawRoundRect(color, Offset(x, w * 0.3f), androidx.compose.ui.geometry.Size(w * 0.12f, w * 0.4f), cr, style = Stroke(sw))
        }
        drawLine(color, Offset(w * 0.08f, w * 0.42f), Offset(w * 0.08f, w * 0.58f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.92f, w * 0.42f), Offset(w * 0.92f, w * 0.58f), sw, StrokeCap.Round)
    }
}
