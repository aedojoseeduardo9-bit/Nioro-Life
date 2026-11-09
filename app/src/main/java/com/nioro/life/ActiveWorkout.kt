package com.nioro.life

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** Pitidos y vibración para el descanso y el temporizador de intervalos. */
object Buzz {
    private var tg: ToneGenerator? = null
    private fun tone(): ToneGenerator? = tg ?: try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 90).also { tg = it }
    } catch (e: Exception) { null }

    fun tick(ctx: Context, cfg: TrainCfg) {
        if (cfg.sound) tone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        if (cfg.vibrate) vibrate(ctx, 35)
    }

    fun phase(ctx: Context, cfg: TrainCfg) {
        if (cfg.sound) tone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        if (cfg.vibrate) vibrate(ctx, 220)
    }

    fun finish(ctx: Context, cfg: TrainCfg) {
        if (cfg.sound) tone()?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 700)
        if (cfg.vibrate) vibrate(ctx, 500)
    }

    fun vibrate(ctx: Context, ms: Long) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) { }
    }
}

/** Mantiene la pantalla encendida mientras esta parte de la app está visible. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

private val REST_OPTIONS = listOf(30, 45, 60, 90, 120, 150, 180, 240, 300)

/** Pantalla del entrenamiento en curso, al estilo de Strong / Hevy. */
@Composable
fun ActiveWorkoutScreen(
    workout: Workout,
    history: List<Workout>,
    custom: List<Exercise>,
    favs: Set<String>,
    cfg: TrainCfg,
    restEnd: Long,
    restTotal: Int,
    onRest: (Long, Int) -> Unit,
    onChange: (Workout) -> Unit,
    onFinish: (Workout) -> Unit,
    onDiscard: () -> Unit,
    onMinimize: () -> Unit
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    var picking by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var infoFor by remember { mutableStateOf<String?>(null) }

    KeepScreenOn()
    BackHandler { onMinimize() }
    // "Anterior" de cada ejercicio: se calcula una vez por historial, no en cada redibujo
    val prevCache = remember(history) { HashMap<String, List<SetLog>>() }
    fun prevOf(id: String) = prevCache.getOrPut(id) { lastSetsFor(id, history) }

    val volume = workoutVolume(workout)
    val doneSets = workoutDoneSets(workout)
    val totalSets = workout.entries.sumOf { it.sets.size }
    val progress by animateFloatAsState(
        if (totalSets == 0) 0f else doneSets.toFloat() / totalSets, spring(stiffness = Spring.StiffnessLow), label = "prog"
    )

    fun update(uid: Long, f: (LogEntry) -> LogEntry?) {
        onChange(workout.copy(entries = workout.entries.mapNotNull { if (it.uid == uid) f(it) else it }))
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .padding(horizontal = 16.dp).padding(top = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glass(Modifier.size(46.dp), radius = 16.dp, onClick = onMinimize) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BackIcon(color = p.title) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    GlowText(workout.name, 20.sp)
                    // el reloj se redibuja solo, sin redibujar toda la lista
                    WorkoutClock(workout.start, "  ·  ${fmtVolume(volume)}  ·  $doneSets/$totalSets series")
                }
                Spacer(Modifier.width(8.dp))
                DialogButton("Terminar", true, Modifier.width(108.dp)) { confirmFinish = true }
            }
            Spacer(Modifier.height(10.dp))
            // barra de progreso de series
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.track)) {
                Box(
                    Modifier.fillMaxWidth(progress).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.6f), p.accent)))
                )
            }
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = if (restEnd > 0) 130.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (workout.entries.isEmpty()) {
                    item {
                        Glass(Modifier.fillMaxWidth().rise()) {
                            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🏋️", fontSize = 40.sp)
                                Spacer(Modifier.height(8.dp))
                                GlowText("Entrenamiento vacío", 18.sp)
                                Text(
                                    "Añade ejercicios de la biblioteca para empezar a registrar tus series.",
                                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                itemsIndexed(workout.entries, key = { _, e -> e.uid }) { idx, e ->
                    val ex = exerciseById(e.exId, custom)
                    // una superserie necesita un ejercicio siguiente; si se borró, el último vuelve a descansar
                    val linked = e.linked && idx < workout.entries.size - 1
                    val prevLinked = idx > 0 && workout.entries[idx - 1].linked
                    EntryCard(
                        e, ex, prev = prevOf(e.exId), inSuperset = linked || prevLinked, linked = linked,
                        modifier = Modifier.animateItem(),
                        onInfo = { infoFor = e.exId },
                        onMenu = { menuFor = e.uid },
                        onRest = {
                            val i = REST_OPTIONS.indexOf(e.restSec)
                            update(e.uid) { it.copy(restSec = REST_OPTIONS[(i + 1) % REST_OPTIONS.size]) }
                        },
                        onSet = { si, s ->
                            val was = e.sets.getOrNull(si)?.done ?: false
                            update(e.uid) { it.copy(sets = it.sets.mapIndexed { j, old -> if (j == si) s else old }) }
                            if (s.done && !was) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!linked && e.restSec > 0) {
                                    onRest(System.currentTimeMillis() + e.restSec * 1000L, e.restSec)
                                }
                            }
                        },
                        onAddSet = {
                            update(e.uid) {
                                val last = it.sets.lastOrNull()
                                it.copy(sets = it.sets + SetLog(last?.reps ?: 10, last?.kg ?: 0.0, false))
                            }
                        },
                        onRemoveSet = { update(e.uid) { it.copy(sets = it.sets.dropLast(1)) } }
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DialogButton("+ Añadir ejercicio", true, Modifier.weight(1f)) { picking = true }
                    }
                    Spacer(Modifier.height(10.dp))
                    DialogButton("Descartar entrenamiento", false, Modifier.fillMaxWidth()) { confirmDiscard = true }
                }
            }
        }

        // Barra flotante del descanso
        AnimatedVisibility(
            visible = restEnd > 0,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            RestBarLive(restEnd, restTotal, onRest)
        }
    }

    if (picking) {
        ExercisePickerDialog(custom, favs, onDismiss = { picking = false }) { ids ->
            var next = (workout.entries.maxOfOrNull { it.uid } ?: 0L) + 1
            val add = ids.map { id ->
                val prev = prevOf(id)
                val ex = exerciseById(id, custom)
                val baseReps = if (ex?.type == EX_TIME) 30 else 10
                LogEntry(
                    id,
                    List(maxOf(3, prev.size.coerceAtMost(5))) { i ->
                        val pv = prev.getOrNull(i) ?: prev.lastOrNull()
                        SetLog(pv?.reps ?: baseReps, pv?.kg ?: 0.0, false)
                    },
                    cfg.defaultRest, uid = next++
                )
            }
            onChange(workout.copy(entries = workout.entries + add))
            picking = false
        }
    }

    val mf = menuFor
    val menuEntry = workout.entries.firstOrNull { it.uid == mf }
    if (menuEntry != null) {
        val idx = workout.entries.indexOf(menuEntry)
        EntryMenuDialog(
            entry = menuEntry,
            name = exerciseById(menuEntry.exId, custom)?.name ?: menuEntry.exId,
            canLink = idx < workout.entries.size - 1,
            onDismiss = { menuFor = null },
            onToggleLink = { update(menuEntry.uid) { it.copy(linked = !it.linked) }; menuFor = null },
            onMove = { d ->
                val l = workout.entries.toMutableList()
                val j = (idx + d).coerceIn(0, l.size - 1)
                if (j != idx) { val t = l[idx]; l[idx] = l[j]; l[j] = t; onChange(workout.copy(entries = l)) }
                menuFor = null
            },
            onNote = { n -> update(menuEntry.uid) { it.copy(note = n) } },
            onRemove = { update(menuEntry.uid) { null }; menuFor = null }
        )
    }

    val inf = infoFor
    if (inf != null) {
        val ex = exerciseById(inf, custom)
        if (ex != null) ExerciseInfoDialog(ex, history, favs.contains(ex.id), onFav = null, onAdd = null) { infoFor = null }
        else infoFor = null
    }

    if (confirmFinish) {
        ConfirmDialog(
            if (doneSets == 0) "No has marcado ninguna serie" else "¿Terminar el entrenamiento?",
            if (doneSets == 0) "Marca con ✓ las series que hiciste. Si terminas así, el entrenamiento se descartará."
            else "Llevas $doneSets series y ${fmtVolume(volume)} de volumen. Las series sin marcar no se guardan.",
            yes = if (doneSets == 0) "Descartar" else "Terminar",
            onNo = { confirmFinish = false }
        ) {
            confirmFinish = false
            if (doneSets == 0) onDiscard()
            else onFinish(
                workout.copy(
                    end = System.currentTimeMillis(),
                    entries = workout.entries.map { e -> e.copy(sets = e.sets.filter { it.done }) }.filter { it.sets.isNotEmpty() }
                )
            )
        }
    }
    if (confirmDiscard) {
        ConfirmDialog("¿Descartar entrenamiento?", "Se borrará lo que registraste en esta sesión.", yes = "Descartar", onNo = { confirmDiscard = false }) {
            confirmDiscard = false
            onDiscard()
        }
    }
}

@Composable
private fun EntryCard(
    e: LogEntry,
    ex: Exercise?,
    prev: List<SetLog>,
    inSuperset: Boolean,
    linked: Boolean,
    modifier: Modifier,
    onInfo: () -> Unit,
    onMenu: () -> Unit,
    onRest: () -> Unit,
    onSet: (Int, SetLog) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit
) {
    val p = LocalPalette.current
    val type = ex?.type ?: EX_WEIGHT
    Glass(modifier.fillMaxWidth(), radius = 26.dp) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(p.chipBg)
                        .border(1.dp, p.chipBorder, RoundedCornerShape(13.dp)).clickable(onClick = onInfo),
                    contentAlignment = Alignment.Center
                ) { Text(ex?.let { MUSCLE_EMOJI[it.muscle] } ?: "🏋️", fontSize = 19.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable(onClick = onInfo)) {
                    Text(
                        ex?.name ?: e.exId, color = p.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        (ex?.let { "${MUSCLE_NAMES[it.muscle]} · ${EQUIP_NAMES[it.equip]}" } ?: "") +
                            (if (inSuperset) "  ·  🔗 Superserie" else ""),
                        color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(p.chipBg).clickable(onClick = onRest)
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (linked) "🔗 sin pausa" else "⏱ ${fmtClock(e.restSec.toLong())}",
                        color = p.title, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
                Box(Modifier.size(34.dp).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
                    Text("⋮", color = p.title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (e.note.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("📝 ${e.note}", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderCell("SERIE", Modifier.width(44.dp))
                HeaderCell("ANTERIOR", Modifier.weight(1.3f))
                if (type != EX_TIME) HeaderCell(if (type == EX_REPS) "+KG" else "KG", Modifier.weight(1f))
                HeaderCell(if (type == EX_TIME) "SEG" else "REPS", Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            e.sets.forEachIndexed { i, s ->
                SetRow(i, s, prev.getOrNull(i), type, e.uid) { onSet(i, it) }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("+ Serie", Modifier.weight(1f), onAddSet)
                if (e.sets.size > 1) SmallButton("− Quitar", Modifier.weight(1f), onRemoveSet)
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text, modifier = modifier, color = LocalPalette.current.muted, fontSize = 10.5.sp,
        fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, letterSpacing = 0.4.sp
    )
}

@Composable
private fun SetRow(i: Int, s: SetLog, prev: SetLog?, type: Int, uid: Long, onChange: (SetLog) -> Unit) {
    val p = LocalPalette.current
    val bg by animateColorAsState(if (s.done) p.income.copy(alpha = 0.16f) else Color.Transparent, tween(260), label = "setBg")
    val checkScale by animateFloatAsState(if (s.done) 1f else 0.85f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), label = "chk")
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // número de serie: tócalo para cambiar el tipo (C = calentamiento, D = drop set, F = al fallo)
        Box(
            Modifier.width(44.dp).height(34.dp).clip(RoundedCornerShape(10.dp))
                .clickable { onChange(s.copy(kind = (s.kind + 1) % SET_KIND_LABEL.size)) },
            contentAlignment = Alignment.Center
        ) {
            val label = if (s.kind == 0) "${i + 1}" else SET_KIND_LABEL[s.kind]
            val c = when (s.kind) { 1 -> p.debt; 2 -> p.accent; 3 -> p.expense; else -> p.title }
            Text(label, color = c, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            prev?.let { if (type == EX_TIME) "${it.reps} s" else if (it.kg > 0) "${fmtKg(it.kg)} × ${it.reps}" else "× ${it.reps}" } ?: "—",
            modifier = Modifier.weight(1.3f), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1
        )
        if (type != EX_TIME) {
            NumField(
                key = "kg$uid-$i", value = if (s.kg > 0) fmtKg(s.kg) else "", hint = prev?.let { fmtKg(it.kg) } ?: "0",
                decimal = true, modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
            ) { onChange(s.copy(kg = it.replace(',', '.').toDoubleOrNull() ?: 0.0)) }
        }
        NumField(
            key = "r$uid-$i", value = if (s.reps > 0) "${s.reps}" else "", hint = prev?.reps?.toString() ?: "0",
            decimal = false, modifier = Modifier.weight(1f).padding(horizontal = 3.dp)
        ) { onChange(s.copy(reps = it.toIntOrNull() ?: 0)) }
        Box(
            Modifier.width(44.dp).height(34.dp), contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.size(32.dp).graphicsLayer { scaleX = checkScale; scaleY = checkScale }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (s.done) p.income else p.chipBg)
                    .border(1.dp, if (s.done) p.income else p.chipBorder, RoundedCornerShape(10.dp))
                    .clickable {
                        // si no escribiste nada, usa lo de la vez anterior
                        var ns = s.copy(done = !s.done)
                        if (ns.done && prev != null) {
                            if (ns.reps == 0) ns = ns.copy(reps = prev.reps)
                            if (ns.kg == 0.0 && type == EX_WEIGHT) ns = ns.copy(kg = prev.kg)
                        }
                        onChange(ns)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = if (s.done) Color(0xFF0B2A1C) else p.muted, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/** Campo numérico pequeño. Guarda su propio texto para que el cursor no salte al escribir. */
@Composable
fun NumField(
    key: String,
    value: String,
    hint: String,
    decimal: Boolean,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    onValue: (String) -> Unit
) {
    val p = LocalPalette.current
    var txt by remember(key) { mutableStateOf(value) }
    // si el valor cambia desde fuera (p. ej. al marcar la serie con lo anterior), lo reflejamos
    LaunchedEffect(value) {
        val a = txt.replace(',', '.').toDoubleOrNull() ?: 0.0
        val b = value.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (a != b) txt = value
    }
    Box(
        modifier.height(34.dp).clip(RoundedCornerShape(10.dp)).background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (txt.isEmpty()) Text(hint, color = p.placeholder, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        BasicTextField(
            value = txt,
            onValueChange = { nv ->
                val clean = nv.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)
                txt = clean
                onValue(clean)
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
            textStyle = TextStyle(color = p.title, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SmallButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(14.dp)).background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = p.title, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Tiempo transcurrido del entrenamiento; se actualiza solo una vez por segundo. */
@Composable
private fun WorkoutClock(start: Long, suffix: String) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(start) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    Text(
        "⏱ ${fmtClock((now - start) / 1000)}$suffix",
        color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1
    )
}

/** Barra del descanso con su propio reloj (el pitido lo da TrainScreen, aunque minimices). */
@Composable
private fun RestBarLive(restEnd: Long, restTotal: Int, onRest: (Long, Int) -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(100) } }
    val left = maxOf(0L, restEnd - now)
    val frac = if (restTotal > 0) (left / 1000f / restTotal).coerceIn(0f, 1f) else 0f
    RestBar(
        (left + 999) / 1000, frac,
        onMinus = { onRest(maxOf(System.currentTimeMillis() + 1000, restEnd - 15_000), restTotal) },
        onPlus = { onRest(restEnd + 15_000, restTotal + 15) },
        onSkip = { onRest(0L, 0) }
    )
}

@Composable
private fun RestBar(leftSec: Long, frac: Float, onMinus: () -> Unit, onPlus: () -> Unit, onSkip: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Brush.verticalGradient(p.dialog))
            .border(1.dp, p.dialogBorder, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = 6.dp.toPx()
                val tl = Offset(sw / 2, sw / 2)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(p.track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                drawArc(p.accent, -90f, 360f * frac, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            Text(fmtClock(leftSec), color = p.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Descanso", color = p.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("Respira y prepárate", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        SmallButton("−15", Modifier.width(50.dp), onMinus)
        Spacer(Modifier.width(6.dp))
        SmallButton("+15", Modifier.width(50.dp), onPlus)
        Spacer(Modifier.width(6.dp))
        SmallButton("Saltar", Modifier.width(64.dp), onSkip)
    }
}

@Composable
private fun EntryMenuDialog(
    entry: LogEntry,
    name: String,
    canLink: Boolean,
    onDismiss: () -> Unit,
    onToggleLink: () -> Unit,
    onMove: (Int) -> Unit,
    onNote: (String) -> Unit,
    onRemove: () -> Unit
) {
    val p = LocalPalette.current
    var note by remember { mutableStateOf(entry.note) }
    GlassDialog(onDismiss) {
        GlowText(name, 19.sp)
        Spacer(Modifier.height(12.dp))
        Text("Nota del ejercicio", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.chipBg)
                .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)).padding(12.dp)
        ) {
            if (note.isEmpty()) Text("Ej: asiento en 4, agarre ancho…", color = p.placeholder, fontSize = 14.sp)
            BasicTextField(
                note, { note = it; onNote(it) },
                textStyle = TextStyle(color = p.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(12.dp))
        if (canLink || entry.linked) {
            DialogButton(if (entry.linked) "🔗 Quitar superserie" else "🔗 Superserie con el siguiente", false, Modifier.fillMaxWidth(), onToggleLink)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("↑ Subir", false, Modifier.weight(1f)) { onMove(-1) }
            DialogButton("↓ Bajar", false, Modifier.weight(1f)) { onMove(1) }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton("Eliminar", false, Modifier.weight(1f), onRemove)
            DialogButton("Listo", true, Modifier.weight(1f), onDismiss)
        }
    }
}

/** Diálogo con el estilo de vidrio de la app. */
@Composable
fun GlassDialog(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(30.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).rise().clip(shape)
                .background(Brush.verticalGradient(p.dialog))
                .border(1.dp, p.dialogBorder, shape)
                .padding(18.dp),
            content = content
        )
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, yes: String, onNo: () -> Unit, onYes: () -> Unit) {
    val p = LocalPalette.current
    GlassDialog(onNo) {
        GlowText(title, 19.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onNo)
            DialogButton(yes, true, Modifier.weight(1f), onYes)
        }
    }
}

/** Resumen al terminar, con confeti y los récords nuevos. */
@Composable
fun WorkoutSummaryDialog(w: Workout, records: List<String>, streak: Int, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth()) {
            val shape = RoundedCornerShape(30.dp)
            Column(
                Modifier.fillMaxWidth().padding(16.dp).rise().clip(shape)
                    .background(Brush.verticalGradient(p.dialog))
                    .border(1.dp, p.dialogBorder, shape)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(if (records.isNotEmpty()) "🏆" else "💪", fontSize = 52.sp, modifier = Modifier.rise(1))
                GlowText("¡Entrenamiento completado!", 21.sp, modifier = Modifier.rise(2))
                Text(w.name, color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.rise(3)) {
                    MiniStat("Duración", fmtClock((w.end - w.start) / 1000), Modifier.weight(1f))
                    MiniStat("Volumen", fmtVolume(workoutVolume(w)), Modifier.weight(1f))
                    MiniStat("Series", "${workoutDoneSets(w)}", Modifier.weight(1f))
                }
                if (streak > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text("🔥 Racha: $streak ${if (streak == 1) "semana" else "semanas"} cumpliendo tu meta",
                        color = p.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                if (records.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("RÉCORDS NUEVOS", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                    records.forEachIndexed { i, r ->
                        Text("★ $r", color = p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.rise(4 + i))
                    }
                }
                Spacer(Modifier.height(16.dp))
                DialogButton("¡Genial!", true, Modifier.fillMaxWidth(), onDismiss)
            }
            Confetti(Modifier.matchParentSize())
        }
    }
    LaunchedEffect(Unit) { Buzz.vibrate(ctx, 60) }
}

@Composable
fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(18.dp)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = p.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = p.muted, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Lluvia de confeti que cae una vez (celebración). */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val t = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(2600)) }
    val bits = remember {
        val r = java.util.Random(5)
        List(46) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.35f, 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 6.28f, r.nextFloat()) }
    }
    val colors = listOf(p.accent, p.income, p.expense, p.title, p.debt)
    Canvas(modifier) {
        val v = t.value
        if (v >= 1f) return@Canvas
        for ((n, b) in bits.withIndex()) {
            val x = b[0] * size.width + sin(v * 9f + b[3]) * 18.dp.toPx()
            val y = -20f + (b[1] + v * b[2]) * size.height
            val a = (1f - v).coerceIn(0f, 1f)
            val c = colors[n % colors.size].copy(alpha = a)
            val rot = v * 12f + b[3]
            val w = 7.dp.toPx() * (0.6f + b[4])
            val h = w * 0.5f
            drawLine(
                c, Offset(x - cos(rot) * w / 2, y - sin(rot) * h), Offset(x + cos(rot) * w / 2, y + sin(rot) * h),
                strokeWidth = h * 1.6f, cap = StrokeCap.Round
            )
        }
    }
}
