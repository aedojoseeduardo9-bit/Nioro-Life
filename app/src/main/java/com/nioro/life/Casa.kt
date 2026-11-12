package com.nioro.life

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Sección Casa: ampolletas liberadas (OpenBeken/Tasmota, por la red local) y equipos de Smart Life
 * (puente con la nube de Tuya), todo en una lista y con los mismos controles.
 */
@Composable
fun CasaScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(SmartHome.load(ctx)) }
    var linked by remember { mutableStateOf(TuyaCloud.cfg(ctx) != null) }
    var cloud by remember { mutableStateOf(if (TuyaCloud.cfg(ctx) != null) TuyaCloud.cached(ctx) else emptyList()) }
    var cloudError by remember { mutableStateOf<String?>(null) }
    var cloudLoading by remember { mutableStateOf(false) }
    var effects by remember { mutableStateOf(TuyaCloud.loadEffects(ctx)) }
    // estado por clave: "l<id>" para las liberadas, "t<id>" para las de Smart Life
    val states = remember { mutableStateMapOf<String, BulbState>().apply { cloud.forEach { put("t${it.id}", tuyaState(it)) } } }
    var openKey by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var editingFx by remember { mutableStateOf(false) }
    // tras tocar algo, se deja de preguntar el estado unos segundos para que no "salte" lo que mueves
    var lastTouch by remember { mutableLongStateOf(0L) }

    fun save(list: List<SmartDevice>) { devices = list; SmartHome.save(ctx, list) }

    // ampolletas liberadas: estado cada 5 s
    LaunchedEffect(devices) {
        while (true) {
            if (System.currentTimeMillis() - lastTouch > 3000) {
                devices.forEach { d ->
                    launch { states["l${d.id}"] = SmartHome.refresh(d.ip, states["l${d.id}"] ?: BulbState()) }
                }
            }
            delay(5000)
        }
    }
    // Smart Life: una sola consulta trae todos los equipos; cada 20 s para cuidar el plan gratuito de Tuya
    LaunchedEffect(linked) {
        while (linked) {
            cloudLoading = cloud.isEmpty()
            try {
                val list = TuyaCloud.devices(ctx)
                cloud = list
                cloudError = null
                if (System.currentTimeMillis() - lastTouch > 4000) list.forEach { states["t${it.id}"] = tuyaState(it) }
            } catch (e: Exception) {
                cloudError = e.message ?: "No se pudo conectar con Smart Life"
            }
            cloudLoading = false
            delay(20_000)
        }
    }

    // envía una orden: la pantalla cambia al instante y luego se confirma con el equipo
    val jobs = remember { HashMap<String, Job>() }
    fun control(key: String, cmd: LightCmd, optimistic: BulbState) {
        lastTouch = System.currentTimeMillis()
        val before = states[key] ?: BulbState()
        states[key] = optimistic
        val slot = key + when (cmd) {
            is LightCmd.Power -> "p"; is LightCmd.Level -> "d"; is LightCmd.Hsv -> "c"
            is LightCmd.White -> "t"; is LightCmd.Fx -> "f"; is LightCmd.Timer -> "x"
        }
        jobs[slot]?.cancel()
        jobs[slot] = scope.launch {
            if (key.startsWith("l")) {
                val d = devices.firstOrNull { "l${it.id}" == key } ?: return@launch
                val c = localCmd(cmd) ?: return@launch
                val power = cmd is LightCmd.Power
                delay(if (power) 0 else 120) // los deslizadores mandan solo el último valor
                val r = SmartHome.send(d.ip, c, optimistic)
                states[key] = if (power || !r.online) r else optimistic.copy(online = true)
            } else {
                val d = cloud.firstOrNull { "t${it.id}" == key } ?: return@launch
                delay(if (cmd is LightCmd.Power) 0 else 300)
                try {
                    TuyaCloud.command(ctx, d.id, tuyaCommands(d, cmd, before))
                    // confirma con lo que informa la nube un momento después
                    delay(1500)
                    val st = TuyaCloud.status(ctx, d.id)
                    if (st.isNotEmpty()) {
                        val nd = d.copy(status = st)
                        cloud = cloud.map { if (it.id == d.id) nd else it }
                        if (System.currentTimeMillis() - lastTouch > 1400) states[key] = tuyaState(nd)
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message ?: "Smart Life no respondió", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openLocal = devices.firstOrNull { "l${it.id}" == openKey }
    val openCloud = cloud.firstOrNull { "t${it.id}" == openKey }
    val isOpen = openLocal != null || openCloud != null
    BackHandler(enabled = isOpen) { openKey = null }
    BackHandler(enabled = !isOpen) { onBack() }

    AnimatedContent(
        targetState = if (isOpen) openKey else null,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) + fadeIn(tween(220)))
                .togetherWith(scaleOut(tween(220), targetScale = 0.92f) + fadeOut(tween(180)))
        },
        label = "casa"
    ) { key ->
        val local = devices.firstOrNull { "l${it.id}" == key }
        val tuya = cloud.firstOrNull { "t${it.id}" == key }
        when {
            local != null -> BulbDetail(
                local.name, "IP ${local.ip} · OpenBeken / Tasmota", states[key!!] ?: BulbState(), LightCaps(), emptyList(),
                offline = "Sin conexión con ${local.ip}",
                onBack = { openKey = null },
                onControl = { cmd, opt -> control(key, cmd, opt) },
                onRename = { n -> save(devices.map { if (it.id == local.id) it.copy(name = n) else it }) },
                onDelete = { openKey = null; states.remove(key); save(devices.filter { it.id != local.id }) }
            )
            tuya != null -> BulbDetail(
                tuya.name, "Smart Life" + (if (tuya.product.isNotBlank()) " · ${tuya.product}" else ""),
                states[key!!] ?: tuyaState(tuya), tuyaCaps(tuya), LIGHT_EFFECTS + effects,
                offline = "Sin conexión: revisa que esté enchufada y con Wi-Fi",
                onBack = { openKey = null },
                onControl = { cmd, opt -> control(key, cmd, opt) },
                onNewEffect = { editingFx = true },
                onDeleteEffect = { fx -> effects = effects - fx; TuyaCloud.saveEffects(ctx, effects) }
            )
            else -> DeviceList(
                devices, cloud, states, linked, cloudError, cloudLoading, onBack,
                onOpen = { openKey = it },
                onPower = { k, on -> control(k, LightCmd.Power(on), (states[k] ?: BulbState()).copy(on = on)) },
                onAdd = { adding = true },
                onGuide = { guide = true },
                onLink = { linking = true },
                onUnlink = { confirmUnlink = true }
            )
        }
    }

    if (adding) {
        AddDeviceDialog(
            known = devices.map { it.ip }.toSet(),
            onDismiss = { adding = false },
            onGuide = { adding = false; guide = true }
        ) { name, ip ->
            save(devices + SmartDevice(System.currentTimeMillis(), name, ip))
            adding = false
        }
    }
    if (guide) FlashGuideDialog { guide = false }
    if (linking) {
        TuyaLinkDialog(onDismiss = { linking = false }) { list ->
            linking = false
            cloud = list
            list.forEach { states["t${it.id}"] = tuyaState(it) }
            cloudError = null
            linked = true
            Toast.makeText(ctx, "¡Smart Life enlazado! ${list.size} ${if (list.size == 1) "equipo" else "equipos"}", Toast.LENGTH_LONG).show()
        }
    }
    if (confirmUnlink) {
        ConfirmDialog(
            "¿Desenlazar Smart Life?", "Nioro Life deja de ver tus equipos de Smart Life y borra las claves guardadas en este teléfono. " +
                "Tus equipos siguen funcionando en Smart Life.", "Desenlazar", onNo = { confirmUnlink = false }
        ) {
            confirmUnlink = false
            TuyaCloud.saveCfg(ctx, null)
            cloud.forEach { states.remove("t${it.id}") }
            cloud = emptyList()
            linked = false
        }
    }
    if (editingFx) {
        EffectEditorDialog(onDismiss = { editingFx = false }) { fx ->
            effects = effects + fx
            TuyaCloud.saveEffects(ctx, effects)
            editingFx = false
        }
    }
}

/** Orden en formato Tasmota/OpenBeken; null si ese firmware no la tiene. */
fun localCmd(cmd: LightCmd): String? = when (cmd) {
    is LightCmd.Power -> if (cmd.on) "Power On" else "Power Off"
    is LightCmd.Level -> "Dimmer ${cmd.pct}"
    is LightCmd.Hsv -> "HSBColor ${cmd.h},${cmd.s},${cmd.v}"
    is LightCmd.White -> "Backlog CT ${cmd.ct}; Dimmer ${cmd.pct}"
    is LightCmd.Fx, is LightCmd.Timer -> null
}

// ---------------------------------------------------------------- lista

@Composable
private fun DeviceList(
    devices: List<SmartDevice>,
    cloud: List<TuyaDevice>,
    states: Map<String, BulbState>,
    linked: Boolean,
    cloudError: String?,
    cloudLoading: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPower: (String, Boolean) -> Unit,
    onAdd: () -> Unit,
    onGuide: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit
) {
    val p = LocalPalette.current
    val allKeys = devices.map { "l${it.id}" } + cloud.map { "t${it.id}" }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 14.dp)
    ) {
        TopBar("Casa", onBack)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (devices.isEmpty() && cloud.isEmpty()) {
                item {
                    Glass(Modifier.fillMaxWidth().rise(0)) {
                        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            BulbGlyph(BulbState(online = true, on = true, white = false, hue = 45, sat = 70), Modifier.size(110.dp))
                            Spacer(Modifier.height(8.dp))
                            GlowText("Tu casa en Nioro Life", 19.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Enlaza tu cuenta de Smart Life para controlar aquí tus ampolletas, con efectos y ajustes extra. " +
                                    "También funcionan las ampolletas liberadas (OpenBeken o Tasmota), directo por tu Wi-Fi.",
                                color = p.muted, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            if (allKeys.size >= 2) {
                item {
                    Row(Modifier.rise(1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton("💡 Encender todo", false, Modifier.weight(1f)) { allKeys.forEach { onPower(it, true) } }
                        DialogButton("🌙 Apagar todo", false, Modifier.weight(1f)) { allKeys.forEach { onPower(it, false) } }
                    }
                }
            }
            // ---- Smart Life
            if (!linked) {
                item {
                    Glass(Modifier.fillMaxWidth().rise(2), onClick = onLink) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge { Text("🔗", fontSize = 24.sp) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                GlowText("Enlazar con Smart Life", 17.sp)
                                Text("Trae tus equipos de Smart Life a Nioro Life", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            ForwardIcon()
                        }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SMART LIFE", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.7.sp,
                            modifier = Modifier.weight(1f).padding(start = 6.dp, top = 6.dp))
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onUnlink).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Desenlazar", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (cloudError != null) {
                    item {
                        Glass(Modifier.fillMaxWidth(), radius = 22.dp) {
                            Text("⚠️ $cloudError", color = p.expense, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
                        }
                    }
                }
                if (cloud.isEmpty() && cloudError == null) {
                    item {
                        Text(
                            if (cloudLoading) "Buscando tus equipos de Smart Life…"
                            else "La cuenta está enlazada pero no trae equipos. Revisa que la región del proyecto sea la misma de tu cuenta de Smart Life.",
                            color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }
                }
                itemsIndexed(cloud, key = { _, d -> "t${d.id}" }) { i, d ->
                    val k = "t${d.id}"
                    DeviceCard(d.name, "Smart Life", states[k], tuyaCaps(d), Modifier.rise(2 + i).animateItem(), { onOpen(k) }) {
                        onPower(k, !(states[k]?.on ?: false))
                    }
                }
            }
            // ---- liberadas
            if (devices.isNotEmpty()) {
                item {
                    Text("LIBERADAS · OPENBEKEN / TASMOTA", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
            }
            itemsIndexed(devices, key = { _, d -> "l${d.id}" }) { i, d ->
                val k = "l${d.id}"
                DeviceCard(d.name, d.ip, states[k], LightCaps(), Modifier.rise(3 + i).animateItem(), { onOpen(k) }) {
                    onPower(k, !(states[k]?.on ?: false))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                DialogButton("+ Añadir ampolleta liberada", false, Modifier.fillMaxWidth(), onAdd)
                Spacer(Modifier.height(10.dp))
                DialogButton("📘 Cómo liberar mi ampolleta de Tuya", false, Modifier.fillMaxWidth(), onGuide)
            }
        }
    }
}

@Composable
private fun DeviceCard(name: String, where: String, st: BulbState?, caps: LightCaps, modifier: Modifier, onOpen: () -> Unit, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val s = st ?: BulbState()
    Glass(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            DeviceGlyph(s, caps, Modifier.size(58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = p.title, fontSize = 16.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        st == null -> "Conectando…"
                        !s.online -> "Sin conexión · $where"
                        !s.on -> "Apagado"
                        !caps.light || !caps.dimmer -> "Encendido"
                        s.scene -> "Efecto en curso"
                        s.white -> "Encendida · ${s.dimmer}%" + if (caps.white) " · ${miredToKelvin(s.ct)} K" else ""
                        else -> "Encendida · ${s.dimmer}% · color"
                    },
                    color = if (st != null && !s.online) p.expense else p.muted,
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
            PowerPill(s.on && s.online, enabled = s.online, onToggle)
        }
    }
}

/** Ampolleta dibujada, o un enchufe si el equipo no es una luz. */
@Composable
private fun DeviceGlyph(s: BulbState, caps: LightCaps, modifier: Modifier) {
    if (caps.light) {
        BulbGlyph(s, modifier)
    } else {
        val p = LocalPalette.current
        val glow by animateFloatAsState(if (s.on && s.online) 1f else 0f, tween(400), label = "plug")
        Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                if (glow > 0f) drawCircle(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.45f * glow), Color.Transparent)), size.minDimension / 2)
            }
            Text("🔌", fontSize = 26.sp)
        }
    }
}

/** Interruptor grande con resorte. */
@Composable
private fun PowerPill(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val x by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "pill")
    val track by animateColorAsState(if (on) p.accent else p.chipBg, tween(220), label = "pillC")
    Box(
        Modifier.width(62.dp).height(36.dp).clip(RoundedCornerShape(18.dp)).background(track)
            .border(1.dp, p.chipBorder, RoundedCornerShape(18.dp))
            .clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onToggle() }
            .padding(4.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height / 2
            val cx = r + (size.width - 2 * r) * x
            drawCircle(if (on) p.onBtnMain else p.title.copy(alpha = if (enabled) 0.9f else 0.35f), r, Offset(cx, r))
        }
    }
}

// ---------------------------------------------------------------- dibujo de la ampolleta

/** Ampolleta dibujada: brilla con su color y su brillo; apagada se ve como vidrio. */
@Composable
fun BulbGlyph(s: BulbState, modifier: Modifier) {
    val p = LocalPalette.current
    val target = if (s.on && s.online) Color(bulbArgb(s)) else p.chipBg
    val col by animateColorAsState(target, tween(450), label = "bulbC")
    val level by animateFloatAsState(if (s.on && s.online) 0.35f + 0.65f * s.dimmer / 100f else 0f, tween(450), label = "bulbL")
    val breathe by rememberInfiniteTransition(label = "bulbB").animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "b")
    Canvas(modifier) {
        val w = size.minDimension
        val c = Offset(size.width / 2, size.height * 0.42f)
        val r = w * 0.3f
        if (level > 0f) {
            drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.55f * level), Color.Transparent), c, w * 0.62f * breathe), w * 0.62f * breathe, c)
        }
        // vidrio
        drawCircle(if (level > 0f) col else p.chipBg, r, c)
        drawCircle(Color.White.copy(alpha = 0.18f + 0.3f * level), r * 0.55f, Offset(c.x - r * 0.25f, c.y - r * 0.28f))
        drawCircle(p.borderTop, r, c, style = Stroke(w * 0.025f))
        // rosca
        val bw = r * 0.95f
        val top = c.y + r * 0.86f
        for (i in 0..2) {
            drawRoundRect(
                p.muted.copy(alpha = 0.85f), Offset(c.x - bw / 2, top + i * w * 0.06f), Size(bw, w * 0.045f), CornerRadius(w * 0.02f)
            )
        }
    }
}

// ---------------------------------------------------------------- detalle

@Composable
private fun BulbDetail(
    title: String,
    info: String,
    s: BulbState,
    caps: LightCaps,
    effects: List<LightEffect>,
    offline: String,
    onBack: () -> Unit,
    onControl: (LightCmd, BulbState) -> Unit,
    onRename: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onNewEffect: (() -> Unit)? = null,
    onDeleteEffect: ((LightEffect) -> Unit)? = null
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var timerLeft by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(top = 14.dp)
    ) {
        TopBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Glass(Modifier.fillMaxWidth().rise(0)) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(190.dp).clip(CircleShape).clickable(enabled = s.online) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onControl(LightCmd.Power(!s.on), s.copy(on = !s.on))
                        },
                        contentAlignment = Alignment.Center
                    ) { DeviceGlyph(s, caps, Modifier.fillMaxSize()) }
                    Text(
                        when {
                            !s.online -> offline
                            s.on -> "Toca para apagar"
                            else -> "Toca para encender"
                        },
                        color = if (!s.online) p.expense else p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                    )
                    if (caps.dimmer) {
                        Spacer(Modifier.height(14.dp))
                        LabeledSlider("☀️ Brillo", "${s.dimmer}%", s.dimmer.toFloat(), 1f..100f, s.online) { v ->
                            val n = v.toInt()
                            onControl(LightCmd.Level(n), s.copy(dimmer = n, on = true))
                        }
                    }
                }
            }
            if (caps.color || caps.white) {
                Spacer(Modifier.height(12.dp))
                Glass(Modifier.fillMaxWidth().rise(1)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (caps.color && caps.white) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Chip("🎨 Color", !s.white && !s.scene) {
                                    val sat = maxOf(s.sat, 60)
                                    onControl(LightCmd.Hsv(s.hue, sat, s.dimmer), s.copy(white = false, scene = false, sat = sat, on = true))
                                }
                                Chip("💡 Blanco", s.white && !s.scene) {
                                    onControl(LightCmd.White(s.ct, s.dimmer), s.copy(white = true, scene = false, on = true))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }
                        val showColor = caps.color && (!s.white || !caps.white)
                        AnimatedContent(targetState = showColor, label = "modo") { color ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (color) {
                                    HueRing(s.hue, s.sat, Modifier.size(230.dp), s.online) { h ->
                                        onControl(LightCmd.Hsv(h, s.sat, s.dimmer), s.copy(hue = h, white = false, scene = false, on = true))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LabeledSlider("Intensidad del color", "${s.sat}%", s.sat.toFloat(), 5f..100f, s.online) { v ->
                                        onControl(LightCmd.Hsv(s.hue, v.toInt(), s.dimmer), s.copy(sat = v.toInt(), white = false, scene = false, on = true))
                                    }
                                } else {
                                    // deslizador al revés: a la izquierda cálido, a la derecha frío
                                    GradientSlider(
                                        "Temperatura", "${miredToKelvin(s.ct)} K",
                                        (653 - s.ct).toFloat(), 153f..500f, listOf(Color(whiteArgb(500)), Color(whiteArgb(153))), s.online
                                    ) { v ->
                                        val ct = 653 - v.toInt()
                                        onControl(LightCmd.White(ct, s.dimmer), s.copy(ct = ct, white = true, scene = false, on = true))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (caps.color || caps.white) {
                Spacer(Modifier.height(12.dp))
                Glass(Modifier.fillMaxWidth().rise(2)) {
                    Column(Modifier.padding(16.dp)) {
                        GlowText("Escenas", 16.sp, glow = false)
                        Spacer(Modifier.height(10.dp))
                        LIGHT_SCENES.filter { if (it.white) caps.white else caps.color }.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { sc -> SceneButton(sc, Modifier.weight(1f), s.online) { applyScene(sc, s, onControl) } }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (caps.effects) {
                Spacer(Modifier.height(12.dp))
                Glass(Modifier.fillMaxWidth().rise(3)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowText("✨ Efectos animados", 16.sp, glow = false, modifier = Modifier.weight(1f))
                            if (onNewEffect != null) {
                                Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onNewEffect).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("+ Crear", color = p.accent, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("La ampolleta recorre los colores sola, aunque cierres la app.", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        effects.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                row.forEach { fx ->
                                    EffectButton(fx, Modifier.weight(1f), s.online, onDelete = if (fx.custom) onDeleteEffect?.let { d -> { d(fx) } } else null) {
                                        onControl(LightCmd.Fx(fx), s.copy(on = true, scene = true))
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            if (caps.timer) {
                Spacer(Modifier.height(12.dp))
                Glass(Modifier.fillMaxWidth().rise(4)) {
                    Column(Modifier.padding(16.dp)) {
                        GlowText("⏲️ Apagar en…", 16.sp, glow = false)
                        Text(timerLeft ?: "Se apaga sola cuando pase el tiempo, aunque cierres la app.", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 120 to "2 h", 0 to "Cancelar").forEach { (m, label) ->
                                Chip(label, false) {
                                    if (!s.online) return@Chip
                                    onControl(LightCmd.Timer(m * 60), s)
                                    val msg = if (m == 0) "Temporizador cancelado." else "Se apagará en $label."
                                    timerLeft = msg
                                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
            }
            if (onRename != null || onDelete != null) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (onRename != null) DialogButton("✏️ Nombre", false, Modifier.weight(1f)) { renaming = true }
                    if (onDelete != null) DialogButton("Quitar", false, Modifier.weight(1f)) { confirmDelete = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(info, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
    if (renaming && onRename != null) {
        NameDialog(title, onDismiss = { renaming = false }) { onRename(it); renaming = false }
    }
    if (confirmDelete && onDelete != null) {
        ConfirmDialog("¿Quitar \"$title\"?", "Se quita de Nioro Life. La ampolleta no se borra ni se resetea.", "Quitar", onNo = { confirmDelete = false }) {
            confirmDelete = false
            onDelete()
        }
    }
}

private fun applyScene(sc: LightScene, s: BulbState, onControl: (LightCmd, BulbState) -> Unit) {
    if (sc.white) {
        onControl(LightCmd.White(sc.ct, sc.dimmer), s.copy(white = true, scene = false, ct = sc.ct, dimmer = sc.dimmer, on = true))
    } else {
        onControl(LightCmd.Hsv(sc.hue, sc.sat, sc.dimmer), s.copy(white = false, scene = false, hue = sc.hue, sat = sc.sat, dimmer = sc.dimmer, on = true))
    }
}

/** Botón de efecto con una franja que muestra sus colores moviéndose. */
@Composable
private fun EffectButton(fx: LightEffect, modifier: Modifier, enabled: Boolean, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val cols = remember(fx) { fx.colors.map { (h, s, v) -> Color(hsvToArgb(h.toFloat(), s / 100f, (0.35f + 0.65f * v / 100f))) } }
    val shift by rememberInfiniteTransition(label = "fx").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000 - fx.speed * 30, easing = androidx.compose.animation.core.LinearEasing)), label = "fxs"
    )
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.chipBg)
                .border(1.dp, (cols.firstOrNull() ?: p.accent).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(fx.emoji, fontSize = 20.sp)
            Text(fx.name, color = p.title, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Canvas(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(5.dp)) {
                if (cols.size == 1) drawRoundRect(cols[0], cornerRadius = CornerRadius(3f))
                else {
                    val w = size.width
                    val ext = cols + cols // franja doble para que se desplace sin saltos
                    drawRoundRect(
                        Brush.horizontalGradient(ext, startX = -w * shift * 2, endX = -w * shift * 2 + w * 2 * 2),
                        cornerRadius = CornerRadius(3f)
                    )
                }
            }
        }
        if (onDelete != null) {
            Box(
                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(p.dialog.first()).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { CloseIcon(9.dp) }
        }
    }
}

@Composable
private fun SceneButton(sc: LightScene, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    val c = Color(if (sc.white) whiteArgb(sc.ct) else hsvToArgb(sc.hue.toFloat(), sc.sat / 100f, 1f))
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(c.copy(alpha = 0.32f), p.chipBg)))
            .border(1.dp, c.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(sc.emoji, fontSize = 20.sp)
        Text(sc.name, color = p.title, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, color = p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Slider(
            value = v.coerceIn(range), onValueChange = onChange, valueRange = range, enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = p.title, activeTrackColor = p.accent, inactiveTrackColor = p.track,
                disabledThumbColor = p.muted, disabledActiveTrackColor = p.track
            )
        )
    }
}

/** Deslizador con el riel pintado en degradado (para la temperatura del blanco). */
@Composable
private fun GradientSlider(
    label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, colors: List<Color>, enabled: Boolean, onChange: (Float) -> Unit
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        Row {
            Text(label, color = p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(value, color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Brush.horizontalGradient(colors)))
            Slider(
                value = v.coerceIn(range), onValueChange = onChange, valueRange = range, enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = p.title, activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent, disabledInactiveTrackColor = Color.Transparent
                )
            )
        }
        Row {
            Text("🔥 Cálido", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Frío ❄️", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Rueda de colores: arrastra o toca para elegir el tono. */
@Composable
private fun HueRing(hue: Int, sat: Int, modifier: Modifier, enabled: Boolean, onHue: (Int) -> Unit) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    var local by remember { mutableIntStateOf(hue) }
    LaunchedEffect(hue) { local = hue }
    fun pick(o: Offset, w: Float, h: Float) {
        val deg = Math.toDegrees(atan2((o.y - h / 2).toDouble(), (o.x - w / 2).toDouble())).toFloat()
        val nh = ((deg + 360f) % 360f).toInt()
        if (nh / 30 != local / 30) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        local = nh
        onHue(nh)
    }
    val hues = remember { List(13) { Color(hsvToArgb(it * 30f, 1f, 1f)) } }
    Canvas(
        modifier.aspectRatio(1f)
            .pointerInput(enabled) { if (enabled) detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(enabled) {
                if (enabled) detectDragGestures(onDragStart = { pick(it, size.width.toFloat(), size.height.toFloat()) }) { ch, _ ->
                    pick(ch.position, size.width.toFloat(), size.height.toFloat())
                }
            }
    ) {
        val ring = size.minDimension * 0.13f
        val r = size.minDimension / 2 - ring / 2 - 8f
        val c = center
        drawCircle(Brush.sweepGradient(hues, c), r, c, style = Stroke(ring))
        // vista previa en el centro
        val col = Color(hsvToArgb(local.toFloat(), sat / 100f, 1f))
        drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.5f), Color.Transparent), c, r * 0.85f), r * 0.85f, c)
        drawCircle(col, r * 0.42f, c)
        drawCircle(p.borderTop, r * 0.42f, c, style = Stroke(3f))
        // perilla
        val a = local / 180f * PI.toFloat()
        val k = Offset(c.x + r * cos(a), c.y + r * sin(a))
        drawCircle(Color.Black.copy(alpha = 0.25f), ring * 0.62f, k + Offset(0f, 3f))
        drawCircle(Color.White, ring * 0.6f, k)
        drawCircle(Color(hsvToArgb(local.toFloat(), 1f, 1f)), ring * 0.45f, k)
        if (!enabled) drawCircle(p.bg.first().copy(alpha = 0.5f), size.minDimension / 2, c)
    }
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    GlassDialog(onDismiss) {
        GlowText("Nombre", 19.sp)
        Spacer(Modifier.height(10.dp))
        Field(name, "Ej: Ampolleta del living") { name = it }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
            DialogButton("Guardar", true, Modifier.weight(1f)) { if (name.isNotBlank()) onSave(name.trim()) }
        }
    }
}

@Composable
private fun Field(value: String, hint: String, password: Boolean = false, number: Boolean = false, onChange: (String) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp))
            .semantics { contentDescription = hint }
            .padding(12.dp)
    ) {
        if (value.isEmpty()) Text(hint, color = p.placeholder, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            value, onChange, singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = when {
                password -> KeyboardType.Password
                number -> KeyboardType.Decimal
                else -> KeyboardType.Text
            }),
            textStyle = TextStyle(color = p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth()
        )
    }
}

// ---------------------------------------------------------------- añadir

@Composable
private fun AddDeviceDialog(known: Set<String>, onDismiss: () -> Unit, onGuide: () -> Unit, onAdd: (String, String) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) } // 0 buscar, 1 manual, 2 wifi de ampolleta nueva
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var found by remember { mutableStateOf<List<SmartHome.Found>?>(null) }
    var ip by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    GlassDialog(onDismiss) {
        GlowText("Añadir ampolleta", 19.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("🔍 Buscar", step == 0) { step = 0 }
            Chip("⌨️ Por IP", step == 1) { step = 1 }
            Chip("📶 Nueva", step == 2) { step = 2 }
        }
        Spacer(Modifier.height(12.dp))
        when (step) {
            0 -> {
                Text(
                    "Busca en tu Wi-Fi las ampolletas con OpenBeken o Tasmota. El teléfono debe estar en el mismo Wi-Fi.",
                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                if (scanning) {
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.track)) {
                        Box(Modifier.fillMaxWidth(progress / 100f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.accent))
                    }
                    Text("Buscando… $progress%", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                } else {
                    DialogButton(if (found == null) "Buscar en mi red" else "Buscar otra vez", true, Modifier.fillMaxWidth()) {
                        if (SmartHome.phoneIp(ctx) == null) {
                            Toast.makeText(ctx, "Conéctate al Wi-Fi de tu casa primero", Toast.LENGTH_LONG).show()
                        } else {
                            scanning = true; progress = 0
                            scope.launch {
                                found = SmartHome.scan(ctx) { progress = it }
                                scanning = false
                            }
                        }
                    }
                }
                val f = found
                if (f != null && !scanning) {
                    Spacer(Modifier.height(10.dp))
                    if (f.isEmpty()) {
                        Text("No encontré ampolletas. Revisa que tengan firmware libre y estén en tu Wi-Fi, o añádela por IP.",
                            color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    f.forEach { x ->
                        val already = x.ip in known
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(p.chipBg)
                                .clickable(enabled = !already) { onAdd(x.name, x.ip) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("💡", fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(x.name, color = p.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                Text(x.ip, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (already) "Ya está" else "Añadir", color = if (already) p.muted else p.accent, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
            1 -> {
                Text("Escribe la IP que muestra la ampolleta en su página o en tu router.", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Field(ip, "IP, ej: 192.168.1.50", number = true) { ip = it.trim() }
                Spacer(Modifier.height(8.dp))
                Field(name, "Nombre, ej: Living") { name = it }
                Spacer(Modifier.height(12.dp))
                DialogButton("Añadir", true, Modifier.fillMaxWidth()) {
                    when {
                        !isIpv4(ip) -> Toast.makeText(ctx, "Esa IP no es válida", Toast.LENGTH_SHORT).show()
                        ip in known -> Toast.makeText(ctx, "Esa ampolleta ya está añadida", Toast.LENGTH_SHORT).show()
                        else -> onAdd(name.trim().ifBlank { "Ampolleta ${ip.substringAfterLast('.')}" }, ip)
                    }
                }
            }
            else -> {
                Text(
                    "Para una ampolleta recién liberada (todavía sin tu Wi-Fi):\n" +
                        "1. Enciéndela. Crea una red llamada OpenBeken_… o tasmota-…\n" +
                        "2. En los ajustes de Wi-Fi del teléfono, conéctate a esa red. Si Android avisa \"sin internet\", elige mantener la conexión.\n" +
                        "3. Vuelve aquí, escribe los datos de tu Wi-Fi y pulsa Enviar.\n" +
                        "4. Vuelve a conectar el teléfono a tu Wi-Fi y usa \"Buscar\".",
                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                SmallButton("Abrir ajustes de Wi-Fi", Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                Spacer(Modifier.height(8.dp))
                Field(ssid, "Nombre de tu Wi-Fi (2,4 GHz)") { ssid = it }
                Spacer(Modifier.height(8.dp))
                Field(pass, "Contraseña del Wi-Fi", password = true) { pass = it }
                Spacer(Modifier.height(12.dp))
                DialogButton(if (sending) "Enviando…" else "Enviar a la ampolleta", true, Modifier.fillMaxWidth()) {
                    if (sending) return@DialogButton
                    if (ssid.isBlank()) { Toast.makeText(ctx, "Escribe el nombre de tu Wi-Fi", Toast.LENGTH_SHORT).show(); return@DialogButton }
                    sending = true
                    scope.launch {
                        val ok = SmartHome.sendWifi(ctx, ssid.trim(), pass)
                        sending = false
                        Toast.makeText(
                            ctx,
                            if (ok) "¡Listo! La ampolleta se reinicia. Vuelve a tu Wi-Fi y pulsa Buscar."
                            else "No respondió. ¿Estás conectado a la red de la ampolleta?",
                            Toast.LENGTH_LONG
                        ).show()
                        if (ok) step = 0
                    }
                }
                Spacer(Modifier.height(8.dp))
                SmallButton("O abrir su página (192.168.4.1)", Modifier.fillMaxWidth()) {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://192.168.4.1")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cerrar", false, Modifier.weight(1f), onDismiss)
            DialogButton("📘 Guía", false, Modifier.weight(1f), onGuide)
        }
    }
}

// ---------------------------------------------------------------- guía

@Composable
private fun FlashGuideDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    GlassDialog(onDismiss) {
        GlowText("Liberar tu ampolleta de Tuya", 19.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            GuideStep("1", "Revisa el chip",
                "Las ampolletas Smart Life traen un chip Wi-Fi. Si es Beken (BK7231T o BK7231N, módulos tipo WB2L, CB2L, WB3S…) se puede liberar. " +
                    "Si es BL602 o de otra marca, normalmente no. Busca tu modelo en la lista de equipos de Elektroda/OpenBeken.")
            GuideStep("2", "Sin soldar: tuya-cloudcutter",
                "Desde un computador con Linux (o una Raspberry Pi) y Wi-Fi, tuya-cloudcutter instala OpenBeken sin abrir la ampolleta. " +
                    "Usa el archivo \"UG\" de OpenBeken para tu chip (ej. OpenBK7231N_UG_…bin) y el perfil de tu modelo.")
            GuideStep("3", "Si no funciona: por cable",
                "Si Tuya ya actualizó tu ampolleta, cloudcutter puede fallar. Entonces hay que abrirla y programarla por cable (UART) con el flasher de OpenBeken.")
            GuideStep("4", "Configura los pines",
                "En la página de la ampolleta (192.168.4.1) aplica la plantilla de tu modelo para que el color y el blanco funcionen bien.")
            GuideStep("5", "Conéctala a Nioro Life",
                "En \"Añadir ampolleta → Nueva\" le envías tu Wi-Fi. Luego \"Buscar\" la encuentra y queda en Casa.")
            Spacer(Modifier.height(6.dp))
            Text(
                "⚠️ Cambiar el firmware tiene riesgo: si el chip no es el correcto, la ampolleta puede quedar inutilizable. " +
                    "Hazlo con calma y con una ampolleta que puedas arriesgar. Desconéctala de la corriente antes de abrirla.",
                color = p.debt, fontSize = 12.5.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("OpenBeken", Modifier.weight(1f)) { open("https://github.com/openshwprojects/OpenBK7231T_App") }
            SmallButton("Cloudcutter", Modifier.weight(1f)) { open("https://github.com/tuya-cloudcutter/tuya-cloudcutter") }
        }
        Spacer(Modifier.height(8.dp))
        DialogButton("Entendido", true, Modifier.fillMaxWidth(), onDismiss)
    }
}

@Composable
private fun GuideStep(n: String, title: String, body: String) {
    val p = LocalPalette.current
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Text(n, color = p.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = p.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text(body, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- enlazar Smart Life

/** Asistente para enlazar Nioro Life con la cuenta de Smart Life (proyecto gratuito en iot.tuya.com). */
@Composable
private fun TuyaLinkDialog(onDismiss: () -> Unit, onLinked: (List<TuyaDevice>) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prev = remember { TuyaCloud.cfg(ctx) }
    var id by remember { mutableStateOf(prev?.accessId ?: "") }
    var secret by remember { mutableStateOf(prev?.secret ?: "") }
    var region by remember { mutableIntStateOf(TUYA_REGIONS.indexOfFirst { it.second == prev?.endpoint }.coerceAtLeast(0)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    GlassDialog(onDismiss) {
        GlowText("Enlazar con Smart Life", 19.sp)
        Text(
            "Se hace una sola vez. Tuya (la empresa detrás de Smart Life) da un acceso gratuito para desarrolladores; " +
                "con él, Nioro Life ve y controla los mismos equipos que Smart Life.",
            color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false)) {
            GuideStep("1", "Crea una cuenta gratis",
                "Abre iot.tuya.com y regístrate (puede ser con el mismo correo de Smart Life). En el teléfono, activa \"Sitio de escritorio\" en el menú de Chrome para verlo completo.")
            SmallButton("Abrir iot.tuya.com", Modifier.fillMaxWidth()) { open("https://iot.tuya.com") }
            GuideStep("2", "Crea un proyecto",
                "Cloud → Development → Create Cloud Project. Ponle cualquier nombre. Industry: Smart Home. Development Method: Smart Home. " +
                    "Data Center: \"Western America\" (para cuentas de Chile y Latinoamérica). Acepta los servicios que propone (IoT Core).")
            GuideStep("3", "Enlaza tu Smart Life",
                "En el proyecto, pestaña Devices → Link App Account → Add App Account. Elige \"Automatic\" y \"Read, Write and Manage\": aparece un código QR. " +
                    "En Smart Life ve a Yo → ícono de escanear (arriba a la derecha) y escanéalo.")
            Text(
                "💡 Si solo tienes este teléfono: saca una captura del QR y, en el escáner de Smart Life, toca el ícono de galería para elegir la captura. " +
                    "Si tu Smart Life no tiene esa opción, abre la página en otro teléfono, tablet o computador y escanéalo desde aquí.",
                color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 38.dp, bottom = 4.dp)
            )
            GuideStep("4", "Copia las claves",
                "Pestaña Overview del proyecto: copia el Access ID (Client ID) y el Access Secret (Client Secret) y pégalos aquí abajo.")
            Spacer(Modifier.height(6.dp))
            Text("Región del proyecto", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TUYA_REGIONS.forEachIndexed { i, (name, _) -> Chip(name, region == i) { region = i } }
            }
            Field(id, "Access ID / Client ID") { id = it.trim() }
            Spacer(Modifier.height(8.dp))
            Field(secret, "Access Secret / Client Secret", password = true) { secret = it.trim() }
            Spacer(Modifier.height(6.dp))
            Text("🔒 Las claves se guardan solo en este teléfono.", color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("⚠️ $error", color = p.expense, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
            DialogButton(if (busy) "Enlazando…" else "Enlazar", true, Modifier.weight(1f)) {
                if (busy) return@DialogButton
                if (id.length < 10 || secret.length < 10) { error = "Pega el Access ID y el Access Secret completos."; return@DialogButton }
                busy = true
                error = null
                val cfg = TuyaCfg(id, secret, TUYA_REGIONS[region].second)
                TuyaCloud.saveCfg(ctx, cfg)
                scope.launch {
                    try {
                        val list = TuyaCloud.devices(ctx)
                        busy = false
                        onLinked(list)
                    } catch (e: Exception) {
                        // si falla, se vuelve a lo que había antes
                        TuyaCloud.saveCfg(ctx, prev)
                        busy = false
                        error = e.message ?: "No se pudo conectar con Tuya. Revisa tu internet."
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- crear efecto

/** Editor de efectos propios: colores, modo (suave o saltos) y velocidad, con vista previa en vivo. */
@Composable
private fun EffectEditorDialog(onDismiss: () -> Unit, onSave: (LightEffect) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✨") }
    var mode by remember { mutableStateOf("gradient") }
    var speed by remember { mutableIntStateOf(50) }
    val colors = remember { androidx.compose.runtime.mutableStateListOf(Triple(330, 90, 100), Triple(200, 90, 100), Triple(45, 95, 100)) }
    var sel by remember { mutableIntStateOf(0) }
    val fx = LightEffect(name.ifBlank { "Mi efecto" }, emoji, mode, speed, colors.toList(), custom = true)

    GlassDialog(onDismiss) {
        GlowText("Crear efecto", 19.sp)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).weight(1f, fill = false), horizontalAlignment = Alignment.CenterHorizontally) {
            EffectPreview(fx, Modifier.size(120.dp))
            Field(name, "Nombre, ej: Mi atardecer") { name = it }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("✨", "🌈", "🔥", "🌊", "🌌", "💓", "🎉", "🌙", "🌸", "🍀").forEach { e -> Chip(e, emoji == e) { emoji = e } }
            }
            Text("Colores (toca uno para cambiarlo)", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                colors.forEachIndexed { i, (h, s, v) ->
                    val c = Color(hsvToArgb(h.toFloat(), s / 100f, 0.35f + 0.65f * v / 100f))
                    Box(
                        Modifier.size(if (i == sel) 38.dp else 32.dp).clip(CircleShape).background(c)
                            .border(if (i == sel) 3.dp else 1.dp, if (i == sel) p.title else p.chipBorder, CircleShape)
                            .clickable { sel = i }
                    )
                }
                if (colors.size < 8) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(p.chipBg).border(1.dp, p.chipBorder, CircleShape)
                            .clickable { colors.add(Triple((colors.last().first + 60) % 360, 90, 100)); sel = colors.size - 1 },
                        contentAlignment = Alignment.Center
                    ) { Text("+", color = p.title, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                }
            }
            val cur = colors.getOrNull(sel)
            if (cur != null) {
                HueRing(cur.first, cur.second, Modifier.size(190.dp), true) { h -> colors[sel] = cur.copy(first = h) }
                LabeledSlider("Intensidad", "${cur.second}%", cur.second.toFloat(), 0f..100f, true) { colors[sel] = colors[sel].copy(second = it.toInt()) }
                LabeledSlider("Brillo", "${cur.third}%", cur.third.toFloat(), 1f..100f, true) { colors[sel] = colors[sel].copy(third = it.toInt()) }
                if (colors.size > 2) {
                    SmallButton("Quitar este color", Modifier.fillMaxWidth()) { colors.removeAt(sel); sel = (sel - 1).coerceAtLeast(0) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("〰️ Suave", mode == "gradient") { mode = "gradient" }
                Chip("⚡ Saltos", mode == "jump") { mode = "jump" }
            }
            LabeledSlider("Velocidad", "$speed%", speed.toFloat(), 1f..100f, true) { speed = it.toInt() }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
            DialogButton("Guardar", true, Modifier.weight(1f)) {
                if (colors.size < 2) Toast.makeText(ctx, "Usa al menos 2 colores", Toast.LENGTH_SHORT).show() else onSave(fx)
            }
        }
    }
}

/** Ampolleta que muestra el efecto en vivo (cómo se verá). */
@Composable
private fun EffectPreview(fx: LightEffect, modifier: Modifier) {
    val n = fx.colors.size.coerceAtLeast(1)
    val stepMs = (3200 - fx.speed * 30).coerceAtLeast(250)
    val t by rememberInfiniteTransition(label = "prev").animateFloat(
        0f, n.toFloat(), infiniteRepeatable(tween(stepMs * n, easing = androidx.compose.animation.core.LinearEasing)), label = "pt"
    )
    val i = t.toInt() % n
    val f = if (fx.mode == "jump") 0f else t - t.toInt()
    val a = fx.colors.getOrElse(i) { Triple(40, 0, 100) }
    val b = fx.colors.getOrElse((i + 1) % n) { a }
    // mezcla el tono por el camino corto de la rueda de color
    var dh = b.first - a.first
    if (dh > 180) dh -= 360
    if (dh < -180) dh += 360
    val h = ((a.first + dh * f) + 360f) % 360f
    val s = a.second + (b.second - a.second) * f
    val v = a.third + (b.third - a.third) * f
    BulbGlyph(BulbState(online = true, on = true, white = false, hue = h.toInt(), sat = s.toInt(), dimmer = v.toInt().coerceIn(1, 100)), modifier)
}
