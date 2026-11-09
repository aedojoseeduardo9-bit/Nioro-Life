package com.nioro.life

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TRAIN_TABS = listOf("Entrenar", "Ejercicios", "Timer", "Progreso")

/** Sección de entrenamiento: rutinas, biblioteca de ejercicios, temporizador y progreso. */
@Composable
fun TrainScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var history by remember { mutableStateOf(TrainStore.loadHistory(ctx)) }
    var routines by remember { mutableStateOf(TrainStore.loadRoutines(ctx)) }
    var custom by remember { mutableStateOf(TrainStore.loadCustom(ctx)) }
    var favs by remember { mutableStateOf(TrainStore.loadFavs(ctx)) }
    var cfg by remember { mutableStateOf(TrainStore.loadCfg(ctx)) }
    var body by remember { mutableStateOf(TrainStore.loadBody(ctx)) }
    var active by remember { mutableStateOf(TrainStore.loadActive(ctx)) }
    var showActive by remember { mutableStateOf(active != null) }
    var tab by remember { mutableIntStateOf(0) }
    var summary by remember { mutableStateOf<Pair<Workout, List<String>>?>(null) }
    val timer = remember { IntervalTimer() }
    // descanso entre series: se guarda para no perderlo al minimizar o salir de la sección
    var rest by remember { mutableStateOf(TrainStore.loadRest(ctx)) }
    val setRest: (Long, Int) -> Unit = { end, total -> rest = end to total; TrainStore.saveRest(ctx, end, total) }

    IntervalEngine(timer, cfg)
    RestEngine(rest.first, cfg) { setRest(0L, 0) }

    val setActive: (Workout?) -> Unit = { w -> active = w; TrainStore.saveActive(ctx, w) }
    val start: (Routine?) -> Unit = { rt ->
        if (active != null) {
            Toast.makeText(ctx, "Ya tienes un entrenamiento en curso", Toast.LENGTH_SHORT).show()
        } else {
            setActive(workoutFromRoutine(rt, history, System.currentTimeMillis(), cfg.defaultRest))
        }
        showActive = true
    }
    val toggleFav: (String) -> Unit = { id ->
        favs = if (favs.contains(id)) favs - id else favs + id
        TrainStore.saveFavs(ctx, favs)
    }

    val aw = active
    if (aw != null && showActive) {
        ActiveWorkoutScreen(
            aw, history, custom, favs, cfg,
            restEnd = rest.first, restTotal = rest.second, onRest = setRest,
            onChange = setActive,
            onFinish = { done ->
                val prs = newRecords(done, history, custom)
                history = history + done
                TrainStore.saveHistory(ctx, history)
                setActive(null)
                setRest(0L, 0)
                showActive = false
                tab = 0
                summary = done to prs
            },
            onDiscard = { setActive(null); setRest(0L, 0); showActive = false },
            onMinimize = { showActive = false }
        )
        return
    }

    BackHandler { onBack() }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp).padding(top = 14.dp)
    ) {
        TopBar("Entrenamiento", onBack)
        TabRow(tab) { tab = it }
        Spacer(Modifier.height(12.dp))
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it * dir / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it * dir / 3 } + fadeOut(tween(160)))
            },
            modifier = Modifier.weight(1f),
            label = "trainTab"
        ) { t ->
            when (t) {
                0 -> StartTab(
                    active, history, routines, cfg, custom,
                    onResume = { showActive = true },
                    onStart = start,
                    onGoal = { g -> cfg = cfg.copy(weeklyGoal = g); TrainStore.saveCfg(ctx, cfg) },
                    onSaveRoutine = { r ->
                        routines = routines.filter { it.id != r.id } + r
                        TrainStore.saveRoutines(ctx, routines)
                    },
                    onDeleteRoutine = { r ->
                        routines = routines.filter { it.id != r.id }
                        TrainStore.saveRoutines(ctx, routines)
                    },
                    favs = favs
                )
                1 -> LibraryTab(
                    custom, favs, history, canAdd = active != null,
                    onFav = toggleFav,
                    onAdd = { id ->
                        val w = active ?: return@LibraryTab
                        val next = (w.entries.maxOfOrNull { it.uid } ?: 0L) + 1
                        val prev = lastSetsFor(id, history)
                        val ex = exerciseById(id, custom)
                        setActive(
                            w.copy(entries = w.entries + LogEntry(
                                id, List(3) { i ->
                                    val pv = prev.getOrNull(i) ?: prev.lastOrNull()
                                    SetLog(pv?.reps ?: if (ex?.type == EX_TIME) 30 else 10, pv?.kg ?: 0.0, false)
                                }, cfg.defaultRest, uid = next
                            ))
                        )
                        Toast.makeText(ctx, "Añadido al entrenamiento", Toast.LENGTH_SHORT).show()
                    },
                    onCreate = { e ->
                        custom = custom + e
                        TrainStore.saveCustom(ctx, custom)
                    }
                )
                2 -> TimerTab(timer, cfg) { c -> cfg = c; TrainStore.saveCfg(ctx, c) }
                else -> ProgressTab(
                    history, custom, cfg, body,
                    onBody = { b -> body = b; TrainStore.saveBody(ctx, b) },
                    onDelete = { w -> history = history.filter { it.id != w.id }; TrainStore.saveHistory(ctx, history) },
                    onRepeat = { w ->
                        start(Routine(0, w.name, w.entries.map { RoutineItem(it.exId, it.sets.size, it.sets.firstOrNull()?.reps ?: 10, it.restSec) }))
                    }
                )
            }
        }
    }

    val sm = summary
    if (sm != null) {
        WorkoutSummaryDialog(sm.first, sm.second, weekStreak(history, cfg.weeklyGoal, System.currentTimeMillis())) { summary = null }
    }
}

@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(20.dp)
    val pos by animateFloatAsState(selected.toFloat(), spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), label = "tab")
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(shape).background(p.chipBg).border(1.dp, p.chipBorder, shape)
    ) {
        // "píldora" que se desliza hasta la pestaña elegida
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val w = size.width / TRAIN_TABS.size
            drawRoundRect(
                Brush.verticalGradient(p.btnMain), Offset(w * pos, 0f), Size(w, size.height), CornerRadius(16.dp.toPx())
            )
        }
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            TRAIN_TABS.forEachIndexed { i, t ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(16.dp)).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t, color = if (i == selected) p.onBtnMain else p.title,
                        fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), color = LocalPalette.current.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.7.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 8.dp)
    )
}

// ---------------------------------------------------------------- ENTRENAR

@Composable
private fun StartTab(
    active: Workout?,
    history: List<Workout>,
    routines: List<Routine>,
    cfg: TrainCfg,
    custom: List<Exercise>,
    onResume: () -> Unit,
    onStart: (Routine?) -> Unit,
    onGoal: (Int) -> Unit,
    onSaveRoutine: (Routine) -> Unit,
    onDeleteRoutine: (Routine) -> Unit,
    favs: Set<String>
) {
    val p = LocalPalette.current
    val now = System.currentTimeMillis()
    var preview by remember { mutableStateOf<Routine?>(null) }
    var editing by remember { mutableStateOf<Routine?>(null) }
    val days = daysThisWeek(history, now)
    val streak = weekStreak(history, cfg.weeklyGoal, now)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (active != null) {
            item { ActiveBanner(active, onResume, Modifier.rise(0)) }
        }
        item {
            Glass(Modifier.fillMaxWidth().rise(1)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GoalRing(days, cfg.weeklyGoal, Modifier.size(92.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText("Meta semanal", 17.sp)
                        Text(
                            if (days >= cfg.weeklyGoal) "¡Cumplida! 🎉 Sigue así." else "Te faltan ${cfg.weeklyGoal - days} ${if (cfg.weeklyGoal - days == 1) "día" else "días"} esta semana",
                            color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "🔥 Racha: $streak ${if (streak == 1) "semana" else "semanas"}",
                            color = p.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (2..6).forEach { g -> Chip("$g días", cfg.weeklyGoal == g) { onGoal(g) } }
                        }
                    }
                }
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().rise(2), onClick = { onStart(null) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge { PlusIcon(26.dp, p.title) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        GlowText("Entrenamiento libre", 17.sp)
                        Text("Empieza vacío y añade lo que vayas haciendo", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    ForwardIcon()
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("Mis rutinas") }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { editing = Routine(System.currentTimeMillis(), "", emptyList()) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) { Text("+ Nueva rutina", color = p.accent, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        if (routines.isEmpty()) {
            item {
                Text(
                    "Crea tus propias rutinas o guarda una de las de abajo para tenerla a mano.",
                    color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        itemsIndexed(routines.sortedByDescending { it.id }, key = { _, r -> "u${r.id}" }) { i, r ->
            RoutineCard(r, custom, Modifier.rise(3 + i).animateItem()) { preview = r }
        }
        item { SectionLabel("Rutinas listas") }
        itemsIndexed(PRESET_ROUTINES, key = { _, r -> "p${r.id}" }) { i, r ->
            RoutineCard(r, custom, Modifier.rise(4 + routines.size + i)) { preview = r }
        }
    }

    val pv = preview
    if (pv != null) {
        RoutinePreviewDialog(
            pv, custom, history,
            onDismiss = { preview = null },
            onStart = { preview = null; onStart(pv) },
            onEdit = { preview = null; editing = if (pv.preset) pv.copy(id = System.currentTimeMillis(), preset = false, name = pv.name) else pv },
            onDelete = if (pv.preset) null else ({ preview = null; onDeleteRoutine(pv) })
        )
    }
    val ed = editing
    if (ed != null) {
        RoutineEditorDialog(ed, custom, favs, onDismiss = { editing = null }) { r -> onSaveRoutine(r); editing = null }
    }
}

@Composable
private fun ActiveBanner(w: Workout, onResume: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val tr = rememberInfiniteTransition(label = "live")
    val pulse by tr.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "dot")
    Glass(modifier.fillMaxWidth(), onClick = onResume) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(p.income.copy(alpha = pulse)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("EN CURSO", color = p.income, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
                GlowText(w.name, 17.sp)
                Text("${fmtClock((now - w.start) / 1000)} · ${workoutDoneSets(w)} series hechas", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Continuar", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun GoalRing(done: Int, goal: Int, modifier: Modifier) {
    val p = LocalPalette.current
    val a = remember { Animatable(0f) }
    val target = if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
    LaunchedEffect(target) { a.animateTo(target, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = 9.dp.toPx()
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(p.track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            if (a.value > 0f) {
                drawArc(p.accent.copy(alpha = 0.25f), -90f, 360f * a.value, false, tl, sz, style = Stroke(sw + 8f, cap = StrokeCap.Round))
                drawArc(p.accent, -90f, 360f * a.value, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done/$goal", color = p.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("días", color = p.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoutineCard(r: Routine, custom: List<Exercise>, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val muscles = r.items.mapNotNull { exerciseById(it.exId, custom)?.muscle }.distinct()
    Glass(modifier.fillMaxWidth(), radius = 26.dp, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge { Text(muscles.firstOrNull()?.let { MUSCLE_EMOJI[it] } ?: "🏋️", fontSize = 24.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { "Rutina" }, color = p.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${r.items.size} ejercicios · ${r.items.sumOf { it.sets }} series" +
                        (if (muscles.isNotEmpty()) " · " + muscles.take(3).joinToString(", ") { MUSCLE_NAMES[it] } else ""),
                    color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (r.desc.isNotBlank()) Text(r.desc, color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            ForwardIcon()
        }
    }
}

@Composable
private fun RoutinePreviewDialog(
    r: Routine,
    custom: List<Exercise>,
    history: List<Workout>,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val p = LocalPalette.current
    GlassDialog(onDismiss) {
        GlowText(r.name.ifBlank { "Rutina" }, 20.sp)
        if (r.desc.isNotBlank()) Text(r.desc, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val estMin = r.items.sumOf { it.sets * (45 + it.restSec) } / 60
        Text("≈ $estMin min · ${r.items.sumOf { it.sets }} series", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            r.items.forEachIndexed { i, it ->
                val ex = exerciseById(it.exId, custom)
                val last = lastSetsFor(it.exId, history).firstOrNull()
                Row(Modifier.padding(vertical = 5.dp).rise(i), verticalAlignment = Alignment.CenterVertically) {
                    Text(ex?.let { e -> MUSCLE_EMOJI[e.muscle] } ?: "•", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ex?.name ?: it.exId, color = p.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${it.sets} × ${it.reps}${if (ex?.type == EX_TIME) " s" else ""} · descanso ${fmtClock(it.restSec.toLong())}" +
                                (last?.let { l -> " · última: ${if (l.kg > 0) fmtKg(l.kg) + " kg × " else ""}${l.reps}" } ?: ""),
                            color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DialogButton("▶  Empezar", true, Modifier.fillMaxWidth(), onStart)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DialogButton(if (r.preset) "Copiar y editar" else "Editar", false, Modifier.weight(1f), onEdit)
            if (onDelete != null) DialogButton("Eliminar", false, Modifier.weight(1f), onDelete)
        }
    }
}

@Composable
private fun RoutineEditorDialog(
    initial: Routine,
    custom: List<Exercise>,
    favs: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Routine) -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    val items = remember { mutableStateListOf<RoutineItem>().apply { addAll(initial.items) } }
    var picking by remember { mutableStateOf(false) }
    GlassDialog(onDismiss) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(p.chipBg)
                .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)).padding(12.dp)
        ) {
            if (name.isEmpty()) Text("Nombre de la rutina", color = p.placeholder, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            BasicTextField(
                name, { name = it }, singleLine = true,
                textStyle = TextStyle(color = p.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            if (items.isEmpty()) {
                Text("Aún no hay ejercicios.", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(6.dp))
            }
            items.forEachIndexed { i, it ->
                val ex = exerciseById(it.exId, custom)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(p.chipBg).padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ex?.name ?: it.exId, color = p.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.size(28.dp).clickable { items.removeAt(i) }, contentAlignment = Alignment.Center) { CloseIcon() }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Stepper("Series", it.sets, 1..10, 1, Modifier.weight(1f)) { v -> items[i] = items[i].copy(sets = v) }
                        Stepper(if (ex?.type == EX_TIME) "Seg" else "Reps", it.reps, 1..600, if (ex?.type == EX_TIME) 5 else 1, Modifier.weight(1f)) { v -> items[i] = items[i].copy(reps = v) }
                        Stepper("Pausa", it.restSec, 0..600, 15, Modifier.weight(1f)) { v -> items[i] = items[i].copy(restSec = v) }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        DialogButton("+ Añadir ejercicios", false, Modifier.fillMaxWidth()) { picking = true }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
            DialogButton("Guardar", true, Modifier.weight(1f)) {
                if (items.isEmpty()) Toast.makeText(ctx, "Añade al menos un ejercicio", Toast.LENGTH_SHORT).show()
                else onSave(initial.copy(name = name.trim().ifBlank { "Mi rutina" }, items = items.toList(), preset = false))
            }
        }
    }
    if (picking) {
        ExercisePickerDialog(custom, favs, onDismiss = { picking = false }) { ids ->
            ids.forEach { id ->
                val ex = exerciseById(id, custom)
                items.add(RoutineItem(id, 3, if (ex?.type == EX_TIME) 30 else 10, 90))
            }
            picking = false
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, range: IntRange, step: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = p.muted, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable { onChange((value - step).coerceIn(range)) }, contentAlignment = Alignment.Center) {
                Text("−", color = p.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                if (label == "Pausa") fmtClock(value.toLong()) else "$value", color = p.title, fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp)
            )
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).clickable { onChange((value + step).coerceIn(range)) }, contentAlignment = Alignment.Center) {
                Text("+", color = p.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

// ---------------------------------------------------------------- EJERCICIOS

private fun filterExercises(all: List<Exercise>, q: String, muscle: Int, equip: Int, onlyFav: Boolean, favs: Set<String>): List<Exercise> {
    val query = q.trim().lowercase()
    return all.filter { e ->
        (muscle < 0 || e.muscle == muscle) && (equip < 0 || e.equip == equip) &&
            (!onlyFav || favs.contains(e.id)) &&
            (query.isEmpty() || e.name.lowercase().contains(query) || MUSCLE_NAMES[e.muscle].lowercase().contains(query))
    }.sortedWith(compareByDescending<Exercise> { favs.contains(it.id) }.thenBy { it.name })
}

@Composable
private fun SearchBox(q: String, onQ: (String) -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.chipBg)
            .border(1.dp, p.chipBorder, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (q.isEmpty()) Text("🔍  Buscar ejercicio…", color = p.placeholder, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        BasicTextField(
            q, onQ, singleLine = true,
            textStyle = TextStyle(color = p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FilterChips(muscle: Int, onMuscle: (Int) -> Unit, onlyFav: Boolean, onFav: () -> Unit, equip: Int, onEquip: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("★ Favoritos", onlyFav, onFav)
        Chip("Todos", muscle < 0) { onMuscle(-1) }
        MUSCLE_NAMES.forEachIndexed { i, m -> Chip("${MUSCLE_EMOJI[i]} $m", muscle == i) { onMuscle(if (muscle == i) -1 else i) } }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Todo el equipo", equip < 0) { onEquip(-1) }
        EQUIP_NAMES.forEachIndexed { i, m -> Chip(m, equip == i) { onEquip(if (equip == i) -1 else i) } }
    }
}

@Composable
private fun LibraryTab(
    custom: List<Exercise>,
    favs: Set<String>,
    history: List<Workout>,
    canAdd: Boolean,
    onFav: (String) -> Unit,
    onAdd: (String) -> Unit,
    onCreate: (Exercise) -> Unit
) {
    val p = LocalPalette.current
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableIntStateOf(-1) }
    var equip by remember { mutableIntStateOf(-1) }
    var onlyFav by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Exercise?>(null) }
    var creating by remember { mutableStateOf(false) }
    val all = EXERCISES + custom
    val list = filterExercises(all, q, muscle, equip, onlyFav, favs)
    Column {
        SearchBox(q) { q = it }
        Spacer(Modifier.height(8.dp))
        FilterChips(muscle, { muscle = it }, onlyFav, { onlyFav = !onlyFav }, equip, { equip = it })
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionLabel("${list.size} de ${all.size} ejercicios") }
            Box(Modifier.clip(RoundedCornerShape(12.dp)).clickable { creating = true }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("+ Crear", color = p.accent, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(list, key = { it.id }) { e ->
                ExerciseRow(e, favs.contains(e.id), Modifier.animateItem(), onFav = { onFav(e.id) }) { info = e }
            }
        }
    }
    val inf = info
    if (inf != null) {
        ExerciseInfoDialog(
            inf, history, favs.contains(inf.id), onFav = { onFav(inf.id) },
            onAdd = if (canAdd) ({ onAdd(inf.id); info = null }) else null
        ) { info = null }
    }
    if (creating) CustomExerciseDialog(onDismiss = { creating = false }) { onCreate(it); creating = false }
}

@Composable
private fun ExerciseRow(e: Exercise, fav: Boolean, modifier: Modifier, selected: Boolean = false, onFav: (() -> Unit)?, onClick: () -> Unit) {
    val p = LocalPalette.current
    val starScale by animateFloatAsState(if (fav) 1.15f else 1f, spring(dampingRatio = 0.3f), label = "star")
    Glass(modifier.fillMaxWidth(), radius = 22.dp, onClick = onClick) {
        Row(
            Modifier.background(if (selected) p.accentSoft else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(p.chipBg).border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(MUSCLE_EMOJI[e.muscle], fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, color = p.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${MUSCLE_NAMES[e.muscle]} · ${EQUIP_NAMES[e.equip]}" + (if (e.custom) " · propio" else ""),
                    color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
            }
            if (selected) {
                Text("✓", color = p.accent, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            } else if (onFav != null) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onFav), contentAlignment = Alignment.Center) {
                    Text(
                        if (fav) "★" else "☆", color = if (fav) p.accent else p.muted, fontSize = (20 * starScale).sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/** Selector a pantalla completa: busca y marca varios ejercicios a la vez. */
@Composable
fun ExercisePickerDialog(custom: List<Exercise>, favs: Set<String>, onDismiss: () -> Unit, onPick: (List<String>) -> Unit) {
    val p = LocalPalette.current
    var q by remember { mutableStateOf("") }
    var muscle by remember { mutableIntStateOf(-1) }
    var equip by remember { mutableIntStateOf(-1) }
    var onlyFav by remember { mutableStateOf(false) }
    val chosen = remember { mutableStateListOf<String>() }
    val list = filterExercises(EXERCISES + custom, q, muscle, equip, onlyFav, favs)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(p.bg))) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 14.dp)
            ) {
                TopBar("Añadir ejercicios", onDismiss)
                SearchBox(q) { q = it }
                Spacer(Modifier.height(8.dp))
                FilterChips(muscle, { muscle = it }, onlyFav, { onlyFav = !onlyFav }, equip, { equip = it })
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(list, key = { it.id }) { e ->
                        ExerciseRow(e, favs.contains(e.id), Modifier, selected = chosen.contains(e.id), onFav = null) {
                            if (chosen.contains(e.id)) chosen.remove(e.id) else chosen.add(e.id)
                        }
                    }
                }
                DialogButton(
                    if (chosen.isEmpty()) "Elige uno o más" else "Añadir ${chosen.size}", true,
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) { if (chosen.isNotEmpty()) onPick(chosen.toList()) }
            }
        }
    }
}

@Composable
fun ExerciseInfoDialog(
    e: Exercise,
    history: List<Workout>,
    fav: Boolean,
    onFav: (() -> Unit)?,
    onAdd: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val p = LocalPalette.current
    val sessions = history.filter { w -> w.entries.any { it.exId == e.id } }.sortedByDescending { it.start }
    val best = bestOneRm(history)[e.id]
    val bestR = bestReps(history)[e.id]
    val df = remember { SimpleDateFormat("d MMM", Locale("es")) }
    GlassDialog(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(MUSCLE_EMOJI[e.muscle], fontSize = 32.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                GlowText(e.name, 19.sp)
                Text("${MUSCLE_NAMES[e.muscle]} · ${EQUIP_NAMES[e.equip]}", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (onFav != null) {
                Box(Modifier.size(40.dp).clickable(onClick = onFav), contentAlignment = Alignment.Center) {
                    Text(if (fav) "★" else "☆", color = if (fav) p.accent else p.muted, fontSize = 24.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("CÓMO HACERLO", color = p.accent, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
        Text(e.tip.ifBlank { "Sin indicaciones." }, color = p.body, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Sesiones", "${sessions.size}", Modifier.weight(1f))
            if (e.type == EX_WEIGHT) MiniStat("1RM estimado", best?.let { "${fmtKg(it)} kg" } ?: "—", Modifier.weight(1f))
            else MiniStat(if (e.type == EX_TIME) "Mejor tiempo" else "Más reps", bestR?.let { if (e.type == EX_TIME) "$it s" else "$it" } ?: "—", Modifier.weight(1f))
        }
        if (sessions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("ÚLTIMAS VECES", color = p.accent, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
            sessions.take(4).forEach { w ->
                val sets = w.entries.first { it.exId == e.id }.sets
                Text(
                    "${df.format(Date(w.start))}:  " + sets.joinToString("  ") { s ->
                        if (e.type == EX_TIME) "${s.reps}s" else if (s.kg > 0) "${fmtKg(s.kg)}×${s.reps}" else "×${s.reps}"
                    },
                    color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cerrar", onAdd == null, Modifier.weight(1f), onDismiss)
            if (onAdd != null) DialogButton("+ Al entrenamiento", true, Modifier.weight(1.4f), onAdd)
        }
    }
}

@Composable
private fun CustomExerciseDialog(onDismiss: () -> Unit, onSave: (Exercise) -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var name by remember { mutableStateOf("") }
    var tip by remember { mutableStateOf("") }
    var muscle by remember { mutableIntStateOf(0) }
    var equip by remember { mutableIntStateOf(0) }
    var type by remember { mutableIntStateOf(EX_WEIGHT) }
    GlassDialog(onDismiss) {
        GlowText("Ejercicio propio", 19.sp)
        Spacer(Modifier.height(10.dp))
        listOf("Nombre" to name, "Indicaciones (opcional)" to tip).forEachIndexed { i, (hint, v) ->
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(p.chipBg)
                    .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)).padding(12.dp)
            ) {
                if (v.isEmpty()) Text(hint, color = p.placeholder, fontSize = 15.sp)
                BasicTextField(
                    v, { if (i == 0) name = it else tip = it },
                    textStyle = TextStyle(color = p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Text("Músculo", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MUSCLE_NAMES.forEachIndexed { i, m -> Chip(m, muscle == i) { muscle = i } }
        }
        Text("Equipo", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EQUIP_NAMES.forEachIndexed { i, m -> Chip(m, equip == i) { equip = i } }
        }
        Text("Se registra con", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Peso y reps", type == EX_WEIGHT) { type = EX_WEIGHT }
            Chip("Solo reps", type == EX_REPS) { type = EX_REPS }
            Chip("Tiempo", type == EX_TIME) { type = EX_TIME }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
            DialogButton("Guardar", true, Modifier.weight(1f)) {
                if (name.isBlank()) Toast.makeText(ctx, "Ponle un nombre", Toast.LENGTH_SHORT).show()
                else onSave(Exercise("c" + System.currentTimeMillis(), name.trim(), muscle, equip, type, tip.trim(), true))
            }
        }
    }
}

// ---------------------------------------------------------------- TEMPORIZADOR

internal data class Phase(val label: String, val secs: Int, val work: Boolean)

private val TIMER_MODES = listOf("Tabata", "EMOM", "HIIT", "Cronómetro")

private fun buildPhases(mode: Int, work: Int, rest: Int, rounds: Int): List<Phase> {
    val out = mutableListOf(Phase("Prepárate", 5, false))
    when (mode) {
        0 -> repeat(8) { i ->
            out += Phase("¡Dale! ${i + 1}/8", 20, true)
            if (i < 7) out += Phase("Descansa", 10, false)
        }
        1 -> repeat(rounds) { i -> out += Phase("Minuto ${i + 1}/$rounds", 60, true) }
        else -> repeat(rounds) { i ->
            out += Phase("Trabajo ${i + 1}/$rounds", work, true)
            if (i < rounds - 1 && rest > 0) out += Phase("Descanso", rest, false)
        }
    }
    return out
}

/** Estado del temporizador de intervalos; vive en TrainScreen para no perderse al cambiar de pestaña. */
class IntervalTimer {
    var mode by mutableIntStateOf(0)
    var work by mutableIntStateOf(40)
    var rest by mutableIntStateOf(20)
    var rounds by mutableIntStateOf(8)
    var running by mutableStateOf(false)
    var acc by mutableLongStateOf(0L)
    var since by mutableLongStateOf(0L)
    val laps = mutableStateListOf<Long>()

    internal fun phases() = buildPhases(mode, work, rest, rounds)
    fun elapsed(now: Long) = acc + if (running) now - since else 0L
    fun reset() { running = false; acc = 0L; laps.clear() }
}

/** Índice de la fase en curso y el momento (ms) en que empezó. */
private fun phaseAt(phases: List<Phase>, elapsed: Long): Pair<Int, Long> {
    var idx = 0
    var startOf = 0L
    while (idx < phases.size && elapsed >= startOf + phases[idx].secs * 1000L) { startOf += phases[idx].secs * 1000L; idx++ }
    return idx to startOf
}

/** Pitidos del temporizador: cambio de fase, últimos 3 segundos y final. */
@Composable
fun IntervalEngine(t: IntervalTimer, cfg: TrainCfg) {
    val ctx = LocalContext.current
    if (t.running) KeepScreenOn()
    LaunchedEffect(t.running, t.mode) {
        if (!t.running || t.mode == 3) return@LaunchedEffect
        val phases = t.phases()
        val total = phases.sumOf { it.secs } * 1000L
        var lastIdx = -1
        var lastSec = -1L
        while (t.running) {
            val e = t.elapsed(System.currentTimeMillis())
            val (idx, startOf) = phaseAt(phases, e)
            if (idx >= phases.size) {
                Buzz.finish(ctx, cfg)
                t.acc = total
                t.running = false
                break
            }
            if (lastIdx != -1 && idx != lastIdx) Buzz.phase(ctx, cfg)
            val sec = (phases[idx].secs * 1000L - (e - startOf) + 999) / 1000
            if (sec in 1..3 && sec != lastSec && idx == lastIdx) Buzz.tick(ctx, cfg)
            lastIdx = idx
            lastSec = sec
            delay(50)
        }
    }
}

/** Pitidos del descanso entre series: sigue sonando aunque minimices el entrenamiento. */
@Composable
fun RestEngine(restEnd: Long, cfg: TrainCfg, onDone: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(restEnd) {
        if (restEnd == 0L) return@LaunchedEffect
        // un descanso que ya terminó hace rato (p. ej. al volver a la app) se borra sin sonar
        if (restEnd - System.currentTimeMillis() < -2000) { onDone(); return@LaunchedEffect }
        var lastSec = -1L
        while (true) {
            val left = restEnd - System.currentTimeMillis()
            if (left <= 0) { Buzz.finish(ctx, cfg); onDone(); break }
            val sec = (left + 999) / 1000
            if (sec <= 3 && sec != lastSec) { Buzz.tick(ctx, cfg); lastSec = sec }
            delay(100)
        }
    }
}

@Composable
private fun TimerTab(t: IntervalTimer, cfg: TrainCfg, onCfg: (TrainCfg) -> Unit) {
    val p = LocalPalette.current
    // reloj solo para dibujar; los pitidos los da IntervalEngine, aunque cambies de pestaña
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(t.running) {
        now = System.currentTimeMillis()
        while (t.running) { now = System.currentTimeMillis(); delay(50) }
    }
    val phases = remember(t.mode, t.work, t.rest, t.rounds) { t.phases() }
    val total = phases.sumOf { it.secs } * 1000L
    val elapsed = t.elapsed(now)
    val (idx, startOf) = if (t.mode != 3) phaseAt(phases, elapsed) else (0 to 0L)
    val finished = t.mode != 3 && idx >= phases.size
    val ph = phases.getOrNull(idx)
    val phLeft = if (ph != null) ph.secs * 1000L - (elapsed - startOf) else 0L
    val phFrac = if (ph != null) (phLeft.toFloat() / (ph.secs * 1000f)).coerceIn(0f, 1f) else 0f
    val secLeft = (phLeft + 999) / 1000

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TIMER_MODES.forEachIndexed { i, m -> Chip(m, t.mode == i) { if (t.mode != i) { t.reset(); t.mode = i } } }
        }
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth().rise(0)) {
            Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val workColor = p.income
                val restColor = p.accent
                val col = when {
                    t.mode == 3 -> p.accent
                    finished -> p.accent
                    ph?.work == true -> workColor
                    else -> restColor
                }
                val tr = rememberInfiniteTransition(label = "timerGlow")
                val breathe by tr.animateFloat(0.7f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "b")
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                        val sw = 12.dp.toPx()
                        val tl = Offset(sw / 2, sw / 2)
                        val sz = Size(size.width - sw, size.height - sw)
                        drawArc(p.track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                        val f = if (t.mode == 3) ((elapsed % 60_000L) / 60_000f) else if (finished) 1f else phFrac
                        val glowA = if (t.running) 0.28f * breathe else 0.18f
                        drawArc(col.copy(alpha = glowA), -90f, 360f * f, false, tl, sz, style = Stroke(sw + 14f, cap = StrokeCap.Round))
                        drawArc(col, -90f, 360f * f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when {
                                t.mode == 3 -> "CRONÓMETRO"
                                finished -> "¡TERMINADO!"
                                else -> ph?.label?.uppercase() ?: ""
                            },
                            color = col, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp
                        )
                        val big = if (t.mode == 3) {
                            val cs = (elapsed / 10) % 100
                            fmtClock(elapsed / 1000) + ".%02d".format(cs)
                        } else if (finished) "🎉" else fmtClock(secLeft)
                        GlowText(big, if (t.mode == 3) 40.sp else 54.sp)
                        if (t.mode != 3 && !finished) {
                            Text("Total ${fmtClock((total - elapsed).coerceAtLeast(0) / 1000)}", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DialogButton(if (t.running) "Pausa" else if (elapsed > 0 && !finished) "Seguir" else "Empezar", true, Modifier.weight(1f)) {
                        if (finished) t.reset()
                        if (t.running) { t.acc += System.currentTimeMillis() - t.since; t.running = false }
                        else { t.since = System.currentTimeMillis(); now = t.since; t.running = true }
                    }
                    if (t.mode == 3 && t.running) {
                        DialogButton("Vuelta", false, Modifier.weight(1f)) { t.laps.add(0, elapsed) }
                    } else {
                        DialogButton("Reiniciar", false, Modifier.weight(1f)) { t.reset() }
                    }
                }
            }
        }
        if (t.mode == 3 && t.laps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    t.laps.forEachIndexed { i, l ->
                        val prev = t.laps.getOrNull(i + 1) ?: 0L
                        Row {
                            Text("Vuelta ${t.laps.size - i}", color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("+" + fmtClock((l - prev) / 1000), color = p.muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(14.dp))
                            Text(fmtClock(l / 1000), color = p.title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
        if (t.mode == 1 || t.mode == 2) {
            Spacer(Modifier.height(12.dp))
            Glass(Modifier.fillMaxWidth().rise(1)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (t.mode == 2) {
                        Stepper("Trabajo", t.work, 5..600, 5, Modifier.weight(1f)) { t.work = it; t.reset() }
                        Stepper("Descanso", t.rest, 0..600, 5, Modifier.weight(1f)) { t.rest = it; t.reset() }
                    }
                    Stepper("Rondas", t.rounds, 1..60, 1, Modifier.weight(1f)) { t.rounds = it; t.reset() }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Glass(Modifier.fillMaxWidth().rise(2)) {
            Column(Modifier.padding(16.dp)) {
                GlowText("Sonido y descanso", 16.sp, glow = false)
                ToggleRow("Pitidos", cfg.sound) { onCfg(cfg.copy(sound = it)) }
                ToggleRow("Vibración", cfg.vibrate) { onCfg(cfg.copy(vibrate = it)) }
                Text("Descanso por defecto entre series", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 90, 120, 180, 240).forEach { s -> Chip(fmtClock(s.toLong()), cfg.defaultRest == s) { onCfg(cfg.copy(defaultRest = s)) } }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OneRmCard(Modifier.rise(3))
        Spacer(Modifier.height(12.dp))
        PlatesCard(Modifier.rise(4))
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.switchOn, checkedThumbColor = p.title, uncheckedTrackColor = p.chipBg, uncheckedThumbColor = p.muted)
        )
    }
}

@Composable
private fun OneRmCard(modifier: Modifier) {
    val p = LocalPalette.current
    var kg by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("") }
    val k = kg.replace(',', '.').toDoubleOrNull() ?: 0.0
    val r = reps.toIntOrNull() ?: 0
    val one = estimate1RM(k, r)
    Glass(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GlowText("Calculadora de 1RM", 16.sp, glow = false)
            Text("Tu peso máximo estimado para una repetición.", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("1rmkg", kg, "kg", true, Modifier.weight(1f).height(44.dp), 17) { kg = it }
                Text("×", color = p.muted, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                NumField("1rmr", reps, "reps", false, Modifier.weight(1f).height(44.dp), 17) { reps = it }
            }
            if (one > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1RM ≈ ", color = p.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    GlowText("${fmtKg(one)} kg", 24.sp)
                }
                Spacer(Modifier.height(6.dp))
                repMaxTable(one).chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        row.forEach { (rr, v) -> MiniStat("$rr reps", fmtKg(Math.round(v * 2) / 2.0), Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatesCard(modifier: Modifier) {
    val p = LocalPalette.current
    var target by remember { mutableStateOf("") }
    var bar by remember { mutableStateOf(20.0) }
    val t = target.replace(',', '.').toDoubleOrNull() ?: 0.0
    val (plates, rest) = platesPerSide(t, bar)
    Glass(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GlowText("Calculadora de discos", 16.sp, glow = false)
            Text("Qué discos poner a cada lado de la barra.", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("plt", target, "Peso total kg", true, Modifier.weight(1f).height(44.dp), 17) { target = it }
                listOf(20.0, 15.0, 10.0).forEach { b -> Chip("Barra ${fmtKg(b)}", bar == b) { bar = b } }
            }
            if (t > bar) {
                Spacer(Modifier.height(12.dp))
                // dibujo de la barra con los discos
                val colors = mapOf(25.0 to Color(0xFFE5484D), 20.0 to Color(0xFF3E7BFA), 15.0 to Color(0xFFF5C451), 10.0 to Color(0xFF3CCB7F), 5.0 to Color(0xFFF4F6FF), 2.5 to Color(0xFF9AA4B8), 1.25 to Color(0xFF6B7385))
                val anim = remember(plates) { Animatable(0f) }
                LaunchedEffect(plates) { anim.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) }
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val cy = size.height / 2
                    drawRoundRect(p.muted, Offset(0f, cy - 4.dp.toPx()), Size(size.width, 8.dp.toPx()), CornerRadius(4.dp.toPx()))
                    var x = size.width * 0.18f
                    drawRoundRect(p.muted, Offset(x - 6.dp.toPx(), cy - 12.dp.toPx()), Size(6.dp.toPx(), 24.dp.toPx()), CornerRadius(2.dp.toPx()))
                    plates.forEachIndexed { i, pl ->
                        val h = size.height * (0.45f + 0.55f * (pl / 25.0).toFloat()) * (0.6f + 0.4f * anim.value)
                        val w = 9.dp.toPx() + (pl / 25.0).toFloat() * 6.dp.toPx()
                        val dx = (1f - anim.value) * 40f * (i + 1)
                        drawRoundRect(colors[pl] ?: p.accent, Offset(x + dx, cy - h / 2), Size(w, h), CornerRadius(3.dp.toPx()))
                        x += w + 2.dp.toPx()
                    }
                }
                Text(
                    "Cada lado: " + if (plates.isEmpty()) "nada" else plates.groupBy { it }.entries.joinToString(" + ") { (k, v) -> "${v.size}×${fmtKg(k)}" },
                    color = p.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold
                )
                if (rest > 0) Text("Sobran ${fmtKg(rest)} kg que no se pueden cargar con discos estándar.", color = p.muted, fontSize = 12.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- PROGRESO

@Composable
private fun ProgressTab(
    history: List<Workout>,
    custom: List<Exercise>,
    cfg: TrainCfg,
    body: List<BodyWeight>,
    onBody: (List<BodyWeight>) -> Unit,
    onDelete: (Workout) -> Unit,
    onRepeat: (Workout) -> Unit
) {
    val p = LocalPalette.current
    val now = System.currentTimeMillis()
    var detail by remember { mutableStateOf<Workout?>(null) }
    val sorted = history.sortedByDescending { it.start }
    val df = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale("es")) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.rise(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Entrenos", "${history.size}", Modifier.weight(1f))
                MiniStat("Horas", "%.1f".format(history.sumOf { maxOf(0L, it.end - it.start) } / 3_600_000.0), Modifier.weight(1f))
                MiniStat("Volumen", fmtVolume(history.sumOf { workoutVolume(it) }), Modifier.weight(1f))
                MiniStat("Racha", "🔥${weekStreak(history, cfg.weeklyGoal, now)}", Modifier.weight(1f))
            }
        }
        item {
            Glass(Modifier.fillMaxWidth().rise(1)) {
                Column(Modifier.padding(16.dp)) {
                    GlowText("Volumen por semana", 16.sp, glow = false)
                    Text("Kilos totales levantados (últimas 8 semanas)", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    WeeklyBars(weeklyVolumes(history, now))
                }
            }
        }
        item {
            val ms = muscleSets(history, now, custom)
            val max = (ms.maxOrNull() ?: 0).coerceAtLeast(1)
            Glass(Modifier.fillMaxWidth().rise(2)) {
                Column(Modifier.padding(16.dp)) {
                    GlowText("Músculos trabajados", 16.sp, glow = false)
                    Text("Series en los últimos 7 días · se recomiendan 10–20 por músculo a la semana", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (ms.all { it == 0 }) Text("Todavía no hay series esta semana.", color = p.muted, fontSize = 13.sp)
                    ms.withIndex().filter { it.value > 0 }.sortedByDescending { it.value }.forEach { (i, v) ->
                        MuscleBar("${MUSCLE_EMOJI[i]} ${MUSCLE_NAMES[i]}", v, v.toFloat() / max)
                    }
                }
            }
        }
        item {
            val best = bestOneRm(history).entries.sortedByDescending { it.value }.take(8)
            if (best.isNotEmpty()) {
                Glass(Modifier.fillMaxWidth().rise(3)) {
                    Column(Modifier.padding(16.dp)) {
                        GlowText("🏆 Récords personales", 16.sp, glow = false)
                        Text("1RM estimado de tus mejores series", color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        best.forEach { (id, v) ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(exerciseById(id, custom)?.name ?: id, color = p.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${fmtKg(v)} kg", color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
        item { BodyWeightCard(body, onBody, Modifier.rise(4)) }
        item { SectionLabel("Historial") }
        if (sorted.isEmpty()) {
            item {
                Text("Aquí aparecerán tus entrenamientos terminados.", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        items(sorted, key = { it.id }) { w ->
            Glass(Modifier.fillMaxWidth().animateItem(), radius = 22.dp, onClick = { detail = w }) {
                Column(Modifier.padding(14.dp)) {
                    Text(w.name, color = p.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "${df.format(Date(w.start))} · ${fmtClock((w.end - w.start) / 1000)} · ${fmtVolume(workoutVolume(w))} · ${workoutDoneSets(w)} series",
                        color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        w.entries.joinToString(", ") { exerciseById(it.exId, custom)?.name ?: it.exId },
                        color = p.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    val d = detail
    if (d != null) {
        var confirm by remember { mutableStateOf(false) }
        GlassDialog({ detail = null }) {
            GlowText(d.name, 19.sp)
            Text(df.format(Date(d.start)), color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Duración", fmtClock((d.end - d.start) / 1000), Modifier.weight(1f))
                MiniStat("Volumen", fmtVolume(workoutVolume(d)), Modifier.weight(1f))
                MiniStat("Series", "${workoutDoneSets(d)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                d.entries.forEach { e ->
                    val ex = exerciseById(e.exId, custom)
                    Text(ex?.name ?: e.exId, color = p.title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 6.dp))
                    e.sets.forEachIndexed { i, s ->
                        Text(
                            "  ${if (s.kind == 0) "${i + 1}" else SET_KIND_LABEL[s.kind]}.  " +
                                if (ex?.type == EX_TIME) "${s.reps} s" else if (s.kg > 0) "${fmtKg(s.kg)} kg × ${s.reps}" else "${s.reps} reps",
                            color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            DialogButton("↻ Repetir este entrenamiento", true, Modifier.fillMaxWidth()) { detail = null; onRepeat(d) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogButton(if (confirm) "¿Seguro?" else "Eliminar", false, Modifier.weight(1f)) {
                    if (confirm) { onDelete(d); detail = null } else confirm = true
                }
                DialogButton("Cerrar", false, Modifier.weight(1f)) { detail = null }
            }
        }
    }
}

@Composable
private fun WeeklyBars(values: List<Double>) {
    val p = LocalPalette.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessVeryLow)) }
    val max = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    Column {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size
            val gap = 8.dp.toPx()
            val bw = (size.width - gap * (n - 1)) / n
            values.forEachIndexed { i, v ->
                val h = (v / max).toFloat() * size.height * a.value
                val x = i * (bw + gap)
                drawRoundRect(p.track, Offset(x, 0f), Size(bw, size.height), CornerRadius(8.dp.toPx()))
                if (h > 0f) {
                    drawRoundRect(
                        Brush.verticalGradient(listOf(p.accent, p.accent.copy(alpha = 0.45f)), startY = size.height - h, endY = size.height),
                        Offset(x, size.height - h), Size(bw, h), CornerRadius(8.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            values.forEachIndexed { i, _ ->
                Text(
                    if (i == values.size - 1) "Esta" else "-${values.size - 1 - i}", color = p.muted, fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
        }
        Text("Esta semana: ${fmtVolume(values.lastOrNull() ?: 0.0)}", color = p.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MuscleBar(label: String, sets: Int, frac: Float) {
    val p = LocalPalette.current
    val a = remember { Animatable(0f) }
    LaunchedEffect(frac) { a.animateTo(frac, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(130.dp), maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(p.track)) {
            Box(
                Modifier.fillMaxWidth(a.value.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.5f), if (sets >= 10) p.income else p.accent)))
            )
        }
        Text("$sets", color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.End, modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun BodyWeightCard(body: List<BodyWeight>, onBody: (List<BodyWeight>) -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    var input by remember { mutableStateOf("") }
    val sorted = body.sortedBy { it.date }
    Glass(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            GlowText("⚖️ Peso corporal", 16.sp, glow = false)
            val last = sorted.lastOrNull()
            val first = sorted.firstOrNull()
            Text(
                if (last == null) "Registra tu peso para ver cómo cambia."
                else "Actual ${fmtKg(last.kg)} kg" + if (first != null && sorted.size > 1) {
                    val d = last.kg - first.kg
                    " · ${if (d >= 0) "+" else ""}${fmtKg(d)} kg desde el inicio"
                } else "",
                color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )
            if (sorted.size >= 2) {
                Spacer(Modifier.height(8.dp))
                val a = remember { Animatable(0f) }
                LaunchedEffect(sorted.size) { a.snapTo(0f); a.animateTo(1f, tween(900)) }
                val pts = sorted.takeLast(20)
                val mn = pts.minOf { it.kg }
                val mx = pts.maxOf { it.kg }
                Canvas(Modifier.fillMaxWidth().height(80.dp)) {
                    val rng = (mx - mn).coerceAtLeast(0.5)
                    val step = size.width / (pts.size - 1)
                    val path = Path()
                    pts.forEachIndexed { i, b ->
                        val x = i * step
                        val y = size.height - ((b.kg - mn) / rng).toFloat() * (size.height - 12f) - 6f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    clipRect(right = size.width * a.value) {
                        drawPath(path, p.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                        pts.forEachIndexed { i, b ->
                            val y = size.height - ((b.kg - mn) / rng).toFloat() * (size.height - 12f) - 6f
                            drawCircle(p.title, 3.dp.toPx(), Offset(i * step, y))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NumField("bw", input, "Tu peso hoy (kg)", true, Modifier.weight(1f).height(44.dp), 16) { input = it }
                DialogButton("Guardar", true, Modifier.width(110.dp)) {
                    val v = input.replace(',', '.').toDoubleOrNull()
                    if (v != null && v > 0) { onBody(body + BodyWeight(System.currentTimeMillis(), v)); input = "" }
                }
            }
        }
    }
}
