package com.nioro.life

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Un botón del dock: a qué pantalla lleva, su ícono y un aviso opcional (punto o número). */
data class DockItem(
    val screen: String,
    val label: String,
    val icon: @Composable () -> Unit,
    val badge: String? = null,
    val badgeColor: Color? = null
)

/**
 * Dock flotante de vidrio. Al tocar un botón, el ícono salta con un rebote y la pantalla se abre
 * creciendo desde ese punto (`onOpen` recibe el origen relativo a la pantalla).
 */
@Composable
fun Dock(items: List<DockItem>, modifier: Modifier = Modifier, onOpen: (String, TransformOrigin) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(30.dp)
    // entrada: sube desde abajo con resorte
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
    // brillo que recorre el borde del dock, muy despacio
    val sheen by rememberInfiniteTransition(label = "dockSheen").animateFloat(
        -0.3f, 1.3f, infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart), label = "sheen"
    )
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
            .graphicsLayer {
                val v = enter.value
                translationY = (1f - v) * 120.dp.toPx()
                alpha = v.coerceIn(0f, 1f)
            }
            .shadow(22.dp, shape, clip = false, ambientColor = p.shadow, spotColor = p.shadow)
            .clip(shape)
            .background(Brush.verticalGradient(p.dialog))
            .background(Brush.linearGradient(p.glass))
            .drawWithContent {
                drawContent()
                val x = size.width * sheen
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, p.glassHighlight.copy(alpha = 0.55f), Color.Transparent),
                        startX = x - size.width * 0.18f, endX = x + size.width * 0.18f
                    )
                )
            }
            .border(1.dp, Brush.verticalGradient(listOf(p.borderTop, p.borderBottom)), shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, it -> DockButton(it, i, Modifier.weight(1f), onOpen) }
    }
}

@Composable
private fun DockButton(item: DockItem, index: Int, modifier: Modifier, onOpen: (String, TransformOrigin) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    val hop = remember { Animatable(0f) }
    // cada ícono aparece un poco después del anterior
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120L + 70L * index)
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    val press by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "dockPress")
    val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(180), label = "dockGlow")

    Column(
        modifier
            .onGloballyPositioned { c ->
                val root = c.findRootCoordinates().size
                val b = c.boundsInRoot()
                if (root.width > 0 && root.height > 0) {
                    origin = TransformOrigin(b.center.x / root.width, b.center.y / root.height)
                }
            }
            .clip(RoundedCornerShape(22.dp))
            .clickable(interactionSource = src, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch {
                    hop.snapTo(0f)
                    hop.animateTo(1f, tween(140))
                    hop.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
                }
                onOpen(item.screen, origin)
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.graphicsLayer {
                val s = press * (0.4f + 0.6f * pop.value)
                scaleX = s
                scaleY = s
                alpha = pop.value.coerceIn(0f, 1f)
                translationY = -hop.value * 10.dp.toPx()
            },
            contentAlignment = Alignment.Center
        ) {
            // halo de luz al pulsar
            Box(
                Modifier.size(64.dp).graphicsLayer { alpha = glow }
                    .background(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.45f), Color.Transparent)), CircleShape)
            )
            IconBadge { item.icon() }
            val b = item.badge
            if (b != null) {
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)
                        .then(if (b.isEmpty()) Modifier.size(12.dp) else Modifier.height(18.dp))
                        .clip(RoundedCornerShape(9.dp))
                        .background(item.badgeColor ?: p.accent)
                        .border(1.5.dp, p.dialog.first(), RoundedCornerShape(9.dp))
                        .padding(horizontal = if (b.isEmpty()) 0.dp else 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (b.isNotEmpty()) Text(b, color = p.onBtnMain, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            item.label, color = p.title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.graphicsLayer { alpha = pop.value.coerceIn(0f, 1f) }
        )
    }
}
