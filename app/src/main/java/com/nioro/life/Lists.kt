package com.nioro.life

import android.app.AlertDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Listas con casillas: compras, repuestos, equipos, lista de deseos… */
@Composable
fun ListsScreen(
    lists: List<CheckList>,
    history: Map<Int, List<HistItem>>,
    onHistory: (Map<Int, List<HistItem>>) -> Unit,
    onSave: (CheckList) -> Unit,
    onDelete: (CheckList) -> Unit,
    onBack: () -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var openId by remember { mutableLongStateOf(-1L) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var createKind by remember { mutableStateOf(0) }
    var collapsed by remember { mutableStateOf(setOf<Int>()) }

    val open = lists.firstOrNull { it.id == openId }
    BackHandler(enabled = open != null) { openId = -1L }

    if (open == null) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)
                .padding(top = 14.dp)
        ) {
            TopBar("Listas", onBack)
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Glass(Modifier.fillMaxWidth(), radius = 22.dp, onClick = { createKind = 0; creating = true }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("+ Nueva lista", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                if (lists.isEmpty()) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CheckIcon(60.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Aún no tienes listas.\nCrea una de compras, repuestos, equipos o deseos.",
                                color = p.muted, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    for (k in 0..4) {
                        val group = lists.filter { it.kind == k }.sortedByDescending { it.created }
                        if (group.isEmpty()) continue
                        val isCollapsed = k in collapsed
                        item(key = "grupo$k") {
                            GroupHeader(
                                kind = k, count = group.size, collapsed = isCollapsed,
                                onToggle = { collapsed = if (isCollapsed) collapsed - k else collapsed + k },
                                onAdd = { createKind = k; creating = true }
                            )
                        }
                        if (!isCollapsed) {
                            items(group, key = { it.id }) { l -> ListCard(l) { openId = l.id } }
                        }
                    }
                }
            }
        }
    } else {
        ListDetail(
            list = open,
            suggestions = suggestionsFor(open, history),
            onAdded = { t, pr -> onHistory(recordHistory(history, open.kind, t, pr)) },
            onChange = onSave,
            onBack = { openId = -1L },
            onRename = { renaming = true },
            onDelete = {
                AlertDialog.Builder(ctx)
                    .setMessage("¿Eliminar la lista \"${open.title}\" con sus ${open.items.size} elementos?")
                    .setPositiveButton("Eliminar") { _, _ ->
                        openId = -1L
                        onDelete(open)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        )
    }

    if (creating) {
        ListDialog(
            heading = "Nueva lista", confirm = "Crear", initialTitle = "", initialKind = createKind,
            onDismiss = { creating = false }
        ) { title, kind ->
            val l = CheckList(
                id = System.currentTimeMillis(),
                title = title.ifBlank { LIST_KIND_NAMES[kind] },
                kind = kind, items = emptyList(), created = System.currentTimeMillis()
            )
            onSave(l)
            creating = false
            openId = l.id
        }
    }
    if (renaming && open != null) {
        ListDialog(
            heading = "Editar lista", confirm = "Guardar", initialTitle = open.title, initialKind = open.kind,
            onDismiss = { renaming = false }
        ) { title, kind ->
            onSave(open.copy(title = title.ifBlank { LIST_KIND_NAMES[kind] }, kind = kind))
            renaming = false
        }
    }
}

private fun pendingTotal(l: CheckList): Long = l.items.filter { !it.done }.sumOf { it.price }

@Composable
private fun ListCard(l: CheckList, onClick: () -> Unit) {
    val p = LocalPalette.current
    val done = l.items.count { it.done }
    val total = l.items.size
    val frac = if (total == 0) 0f else done.toFloat() / total
    Glass(Modifier.fillMaxWidth(), radius = 26.dp, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(LIST_KIND_EMOJI.getOrElse(l.kind) { "📋" }, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    GlowText(l.title, 18.sp, glow = false)
                    Text(
                        when {
                            total == 0 -> "Vacía"
                            done == total -> "Todo listo ✓ ($total)"
                            else -> "$done de $total listos"
                        },
                        color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                val pt = pendingTotal(l)
                if (pt > 0) {
                    Text(fmtMoney(pt), color = p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.track)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(frac).clip(RoundedCornerShape(3.dp)).background(p.accent))
                }
            }
        }
    }
}

@Composable
private fun ListDetail(
    list: CheckList,
    suggestions: List<HistItem>,
    onAdded: (String, Long) -> Unit,
    onChange: (CheckList) -> Unit,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    val done = list.items.count { it.done }
    val ordered = list.items.sortedBy { it.done }
    val pending = pendingTotal(list)
    val hasPrices = list.items.any { it.price > 0 }

    fun addItem(t: String, price: Long) {
        val item = CheckItem(id = System.nanoTime(), text = t, price = price, done = false)
        onChange(list.copy(items = list.items + item))
        onAdded(t, price)
    }

    fun add() {
        val t = text.trim()
        if (t.isEmpty()) return
        val price = evalExpr(priceText)?.let { Math.round(it) }?.takeIf { it > 0 } ?: 0L
        addItem(t, price)
        text = ""
        priceText = ""
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .padding(horizontal = 16.dp).padding(top = 14.dp)
    ) {
        TopBar(list.title, onBack)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Glass(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(LIST_KIND_EMOJI.getOrElse(list.kind) { "📋" }, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (list.items.isEmpty()) "Agrega tu primer elemento abajo"
                                    else "$done de ${list.items.size} listos",
                                    color = p.body, fontSize = 15.sp, fontWeight = FontWeight.Bold
                                )
                                if (hasPrices) {
                                    Text(
                                        "Falta por ${fmtMoney(pending)}",
                                        color = p.accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Editar", false, onRename)
                            if (done > 0) Chip("Quitar hechos", false) {
                                onChange(list.copy(items = list.items.filter { !it.done }))
                            }
                            if (done > 0) Chip("Desmarcar", false) {
                                onChange(list.copy(items = list.items.map { it.copy(done = false) }))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row { Chip("Eliminar lista", false, onDelete) }
                    }
                }
            }
            items(ordered, key = { row -> row.id }) { row ->
                ItemRow(
                    row,
                    onToggle = { onChange(list.copy(items = list.items.map { x -> if (x.id == row.id) x.copy(done = !x.done) else x })) },
                    onDelete = { onChange(list.copy(items = list.items.filter { x -> x.id != row.id })) }
                )
            }
        }

        if (suggestions.isNotEmpty()) {
            Text(
                "PARA AÑADIR (toca para agregar)", color = p.muted, fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (sg in suggestions) {
                    Chip("+ ${sg.text}", false) { addItem(sg.text, sg.price) }
                }
            }
        }

        // Fila para agregar elementos
        Glass(Modifier.fillMaxWidth().padding(bottom = 10.dp), radius = 24.dp) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text("Elemento") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    colors = fieldColors(), modifier = Modifier.weight(1f)
                )
                TextField(
                    value = priceText, onValueChange = { priceText = it },
                    placeholder = { Text("$") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    colors = fieldColors(), modifier = Modifier.width(92.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(15.dp))
                        .background(Brush.linearGradient(p.fab)).clickable { add() },
                    contentAlignment = Alignment.Center
                ) { PlusIcon(24.dp) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: CheckItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val p = LocalPalette.current
    Glass(Modifier.fillMaxWidth(), radius = 20.dp, onClick = onToggle) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val box = RoundedCornerShape(9.dp)
            Box(
                Modifier.size(28.dp).clip(box)
                    .background(if (item.done) p.accent else p.chipBg)
                    .border(1.5.dp, if (item.done) p.accent else p.chipBorder, box),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) Text("✓", color = p.onBtnMain, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                item.text,
                modifier = Modifier.weight(1f),
                color = if (item.done) p.muted else p.body,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
            if (item.price > 0) {
                Spacer(Modifier.width(8.dp))
                Text(fmtMoney(item.price), color = if (item.done) p.muted else p.accent, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(11.dp)).background(p.chipBg)
                    .border(1.dp, p.chipBorder, RoundedCornerShape(11.dp)).clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) { CloseIcon(13.dp) }
        }
    }
}

@Composable
private fun ListDialog(
    heading: String,
    confirm: String,
    initialTitle: String,
    initialKind: Int,
    onDismiss: () -> Unit,
    onOk: (String, Int) -> Unit
) {
    val p = LocalPalette.current
    var title by remember { mutableStateOf(initialTitle) }
    var kind by remember { mutableStateOf(initialKind) }
    val shape = RoundedCornerShape(30.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(shape)
                .background(Brush.verticalGradient(p.dialog))
                .border(1.dp, p.dialogBorder, shape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            GlowText(heading, 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in 0..2) Chip("${LIST_KIND_EMOJI[k]} ${LIST_KIND_NAMES[k]}", kind == k) { kind = k }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in 3..4) Chip("${LIST_KIND_EMOJI[k]} ${LIST_KIND_NAMES[k]}", kind == k) { kind = k }
            }
            Spacer(Modifier.height(8.dp))
            TextField(
                value = title, onValueChange = { title = it },
                placeholder = { Text("Nombre (ej: ${LIST_KIND_NAMES[kind]} de la semana)") },
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Cancelar", false, Modifier.weight(1f), onDismiss)
                DialogButton(confirm, true, Modifier.weight(1f)) { onOk(title.trim(), kind) }
            }
        }
    }
}


@Composable
private fun GroupHeader(kind: Int, count: Int, collapsed: Boolean, onToggle: () -> Unit, onAdd: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onToggle).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (collapsed) "▸" else "▾", color = p.muted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.width(8.dp))
        Text(LIST_KIND_EMOJI.getOrElse(kind) { "📋" }, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "${LIST_KIND_NAMES.getOrElse(kind) { "Otra" }.uppercase()} ($count)",
            color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        Chip("+ Nueva", false, onAdd)
    }
}

private val SEEDS = mapOf(
    0 to listOf("Pan", "Leche", "Huevos", "Arroz", "Fideos", "Aceite", "Azúcar", "Café", "Papel higiénico", "Detergente"),
    1 to listOf("Filtro de aceite", "Filtro de aire", "Bujías", "Pastillas de freno", "Batería", "Correa"),
    2 to listOf("Notebook", "Celular", "Cargador", "Audífonos", "Mouse", "Teclado")
)

/** Cosas para añadir a una lista: lo que ya agregaste antes a listas de su tipo (lo más usado primero) y algunas ideas. */
fun suggestionsFor(list: CheckList, history: Map<Int, List<HistItem>>): List<HistItem> {
    val have = list.items.map { it.text.trim().lowercase() }.toSet()
    val hist = (history[list.kind] ?: emptyList()).sortedByDescending { it.count }
    val seen = hist.map { it.text.trim().lowercase() }.toSet()
    val seeds = (SEEDS[list.kind] ?: emptyList()).filter { it.lowercase() !in seen }.map { HistItem(it, 0L, 0) }
    return (hist + seeds).filter { it.text.trim().lowercase() !in have }.take(14)
}

/** Anota que agregaste `text` a una lista de tipo `kind` (cuenta cuántas veces y recuerda el último precio). */
fun recordHistory(history: Map<Int, List<HistItem>>, kind: Int, text: String, price: Long): Map<Int, List<HistItem>> {
    val clean = text.trim()
    if (clean.isEmpty()) return history
    val cur = history[kind] ?: emptyList()
    val key = clean.lowercase()
    val old = cur.firstOrNull { it.text.trim().lowercase() == key }
    val upd = HistItem(clean, if (price > 0) price else old?.price ?: 0L, (old?.count ?: 0) + 1)
    val list = (cur.filter { it !== old } + upd).sortedByDescending { it.count }.take(60)
    return history + (kind to list)
}
