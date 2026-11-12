package com.nioro.life

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var resumeTick by mutableIntStateOf(0)
    /** Pantalla pedida desde fuera (p. ej. el acceso directo "Nioro IA"). */
    private var openRequest by mutableStateOf<String?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        Scheduler.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Scheduler.scheduleAll(this)
        openRequest = intent?.getStringExtra("open")
        setContent { NioroApp(resumeTick, openRequest) { openRequest = null } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("open")?.let { openRequest = it }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
fun NioroApp(resumeTick: Int, openRequest: String? = null, onOpened: () -> Unit = {}) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(Store.loadSettings(ctx)) }
    var notes by remember { mutableStateOf(Store.loadNotes(ctx)) }
    var tx by remember { mutableStateOf(Store.loadTx(ctx)) }
    var cobros by remember { mutableStateOf(Store.loadCobros(ctx)) }
    var lists by remember { mutableStateOf(Store.loadLists(ctx)) }
    var history by remember { mutableStateOf(Store.loadHistory(ctx)) }
    var trainActive by remember { mutableStateOf(TrainStore.loadActive(ctx)) }
    var screen by remember { mutableStateOf("home") }
    // punto del dock desde donde crece la pantalla que se abre (y hacia donde se encoge al volver)
    var dockOrigin by remember { mutableStateOf(TransformOrigin(0.5f, 0.92f)) }
    var showSettings by remember { mutableStateOf(false) }
    // al abrir el asistente desde la burbuja o el acceso directo, saluda y escucha
    var aiGreet by remember { mutableStateOf(false) }
    // punto de la burbuja desde donde crece el asistente (y hacia donde se encoge al cerrarlo)
    var aiOrigin by remember { mutableStateOf(TransformOrigin(0.85f, 0.6f)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(resumeTick) {
        settings = Store.loadSettings(ctx)
        notes = Store.loadNotes(ctx)
        tx = Store.loadTx(ctx)
        cobros = Store.loadCobros(ctx)
        lists = Store.loadLists(ctx)
        history = Store.loadHistory(ctx)
    }
    // al volver al inicio, recarga lo que pudo cambiar en otras pantallas (p. ej. lo que anotó el asistente)
    LaunchedEffect(screen, resumeTick) {
        if (screen == "home") {
            trainActive = TrainStore.loadActive(ctx)
            settings = Store.loadSettings(ctx)
            notes = Store.loadNotes(ctx)
            tx = Store.loadTx(ctx)
            cobros = Store.loadCobros(ctx)
            lists = Store.loadLists(ctx)
            history = Store.loadHistory(ctx)
        }
    }
    LaunchedEffect(openRequest) {
        val r = openRequest ?: return@LaunchedEffect
        aiGreet = r == "ai"
        if (r == "ai") aiOrigin = TransformOrigin(0.5f, 0.45f)
        screen = r
        onOpened()
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    BackHandler(enabled = screen != "home" && screen != "train" && screen != "casa") { screen = "home" }

    val palette = paletteFor(settings.theme)

    // Íconos de la barra de estado oscuros en el tema claro y claros en el oscuro.
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            val c = WindowCompat.getInsetsController(window, view)
            c.isAppearanceLightStatusBars = palette.light
            c.isAppearanceLightNavigationBars = palette.light
        }
    }

    val updateSettings: (Settings) -> Unit = {
        settings = it
        Store.saveSettings(ctx, it)
        Scheduler.scheduleAll(ctx)
    }
    val saveNote: (Note) -> Unit = { n ->
        val list = notes.filter { it.id != n.id } + n
        notes = list
        Store.saveNotes(ctx, list)
        Scheduler.scheduleAll(ctx)
    }
    val deleteNote: (Note) -> Unit = { n ->
        Scheduler.cancelNote(ctx, n.id)
        val list = notes.filter { it.id != n.id }
        notes = list
        Store.saveNotes(ctx, list)
        Scheduler.scheduleAll(ctx)
    }
    val saveTx: (Tx) -> Unit = { t ->
        val list = tx.filter { it.id != t.id } + t
        tx = list
        Store.saveTx(ctx, list)
    }
    val saveCobro: (Cobro) -> Unit = { c ->
        val list = cobros.filter { it.id != c.id } + c
        cobros = list
        Store.saveCobros(ctx, list)
    }
    val deleteCobro: (Cobro) -> Unit = { c ->
        val list = cobros.filter { it.id != c.id }
        cobros = list
        Store.saveCobros(ctx, list)
    }
    val saveList: (CheckList) -> Unit = { l ->
        val list = lists.filter { it.id != l.id } + l
        lists = list
        Store.saveLists(ctx, list)
    }
    val deleteList: (CheckList) -> Unit = { l ->
        val list = lists.filter { it.id != l.id }
        lists = list
        Store.saveLists(ctx, list)
    }
    val deleteTx: (Tx) -> Unit = { t ->
        val list = tx.filter { it.id != t.id }
        tx = list
        Store.saveTx(ctx, list)
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        val scheme = if (palette.light) {
            lightColorScheme(primary = palette.accent, background = palette.bg[0], surface = palette.bg[1])
        } else {
            darkColorScheme(primary = palette.accent, background = palette.bg[0], surface = palette.bg[1])
        }
        MaterialTheme(colorScheme = scheme) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(palette.bg))) {
                Ambient()
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val dockScreens = setOf("train", "finance", "lists", "casa")
                        val o = dockOrigin
                        val grow = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                        val spec = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
                        val ai = aiOrigin
                        when {
                            // la burbuja se abre: el asistente nace de ella y se expande
                            targetState == "ai" ->
                                (scaleIn(spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.05f, transformOrigin = ai) +
                                    fadeIn(tween(180)))
                                    .togetherWith(fadeOut(tween(260)) + scaleOut(tween(320), targetScale = 0.94f))
                            // al cerrarlo, se encoge de vuelta hacia la burbuja
                            initialState == "ai" && targetState == "home" ->
                                ((fadeIn(tween(280)) + scaleIn(tween(340), initialScale = 0.94f))
                                    .togetherWith(scaleOut(tween(300), targetScale = 0.05f, transformOrigin = ai) + fadeOut(tween(260))))
                                    .apply { targetContentZIndex = -1f }
                            // desde el dock: la pantalla nace del ícono y se expande
                            initialState == "home" && targetState in dockScreens ->
                                (scaleIn(grow, initialScale = 0.12f, transformOrigin = o) + fadeIn(tween(200)))
                                    .togetherWith(scaleOut(tween(320), targetScale = 0.9f) + fadeOut(tween(280)))
                            // de vuelta al inicio: la pantalla se encoge hacia su ícono del dock
                            targetState == "home" && initialState in dockScreens ->
                                ((scaleIn(tween(340), initialScale = 0.9f) + fadeIn(tween(280)))
                                    .togetherWith(scaleOut(tween(300), targetScale = 0.12f, transformOrigin = o) + fadeOut(tween(260))))
                                    .apply { targetContentZIndex = -1f }
                            targetState == "home" ->
                                (fadeIn(tween(260)) + scaleIn(tween(320), initialScale = 0.94f))
                                    .togetherWith(slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(200)))
                            else ->
                                (slideInHorizontally(spec) { it / 3 } + fadeIn(tween(260)))
                                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(260), targetScale = 0.94f))
                        }
                    },
                    label = "nav"
                ) { s ->
                    when (s) {
                        "clock" -> ClockScreen(settings, updateSettings) { screen = "home" }
                        "notes" -> NotesScreen(notes, saveNote, deleteNote) { screen = "home" }
                        "finance" -> FinanceScreen(tx, cobros, saveTx, deleteTx, saveCobro, deleteCobro) { screen = "home" }
                        "train" -> TrainScreen { screen = "home" }
                        "casa" -> CasaScreen { screen = "home" }
                        "ai" -> AssistantScreen(onBack = { screen = "home" }, greetOnOpen = aiGreet, onNavigate = { aiGreet = false; screen = it })
                        "lists" -> ListsScreen(
                            lists, history,
                            onHistory = { h -> history = h; Store.saveHistory(ctx, h) },
                            onSave = saveList, onDelete = deleteList
                        ) { screen = "home" }
                        else -> HomeScreen(
                            settings, notes, tx, cobros, lists, now,
                            onGo = { screen = it },
                            onMenu = { showSettings = true }
                        )
                    }
                }
                AnimatedVisibility(
                    visible = screen == "home",
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) { it } + fadeIn(tween(250)),
                    exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
                ) {
                    val pendingItems = lists.sumOf { l -> l.items.count { !it.done } }
                    val balance = monthSummary(tx, now).balance
                    Dock(
                        listOf(
                            DockItem(
                                "train", "Entreno", { DumbbellIcon() },
                                badge = if (trainActive != null) "" else null, badgeColor = palette.income
                            ),
                            DockItem(
                                "finance", "Finanzas", { CoinIcon() },
                                badge = if (balance < 0) "!" else null, badgeColor = palette.expense
                            ),
                            DockItem("casa", "Casa", { BulbIcon() }),
                            DockItem(
                                "lists", "Listas", { CheckIcon() },
                                badge = if (pendingItems > 0) (if (pendingItems > 99) "99+" else "$pendingItems") else null
                            )
                        )
                    ) { target, origin ->
                        dockOrigin = origin
                        screen = target
                    }
                }
                AssistantBubble(visible = screen != "ai") { origin ->
                    aiOrigin = origin
                    aiGreet = true
                    screen = "ai"
                }
                if (showSettings) {
                    SettingsDialog(
                        settings = settings,
                        onTheme = { t -> updateSettings(settings.copy(theme = t)) },
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }
}
