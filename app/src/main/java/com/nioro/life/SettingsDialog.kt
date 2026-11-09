package com.nioro.life

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Menú de configuración: apariencia (noche / día) y actualización desde GitHub. */
@Composable
fun SettingsDialog(settings: Settings, onTheme: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(30.dp)

    // 0 reposo, 1 buscando, 2 al día, 3 hay versión nueva, 4 descargando, 5 lista, 6 error
    var uState by remember { mutableIntStateOf(0) }
    var uMsg by remember { mutableStateOf("Funciona con Wi-Fi o con datos móviles.") }
    var progress by remember { mutableIntStateOf(0) }
    var remote by remember { mutableStateOf<Updater.Remote?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    var exportScope by remember { mutableIntStateOf(Export.THIS_MONTH) }
    var exportMsg by remember { mutableStateOf("") }

    fun openLink(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            exportMsg = "No se pudo abrir el enlace."
        }
    }

    val installed = remember { Updater.installedBuild(ctx) }
    val versionName = remember { Updater.installedName(ctx) }

    fun install(f: File) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            uMsg = "Activa «Permitir de esta fuente» para Nioro Life y vuelve a pulsar Instalar."
            ctx.startActivity(
                Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        ctx.startActivity(Updater.installIntent(ctx, f))
    }

    fun check() {
        if (!Updater.isOnline(ctx)) {
            uState = 6
            uMsg = "Sin conexión a internet. Activa el Wi-Fi o los datos móviles."
            return
        }
        uState = 1
        uMsg = "Buscando actualizaciones…"
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { Updater.fetchRemote() }
                remote = r
                if (r.build > installed) {
                    uState = 3
                    uMsg = "Hay una versión nueva (compilación ${r.build})."
                } else {
                    uState = 2
                    uMsg = "Ya tienes la última versión."
                }
            } catch (e: Exception) {
                uState = 6
                uMsg = "No se pudo buscar: ${e.message ?: "sin conexión"}"
            }
        }
    }

    fun startDownload() {
        val r = remote ?: return
        uState = 4
        progress = 0
        uMsg = "Descargando…"
        scope.launch {
            try {
                val f = withContext(Dispatchers.IO) { Updater.download(ctx, r) { progress = it } }
                apk = f
                uState = 5
                uMsg = "Descarga lista. Android te pedirá confirmar la instalación."
                install(f)
            } catch (e: Exception) {
                uState = 6
                uMsg = "Falló la descarga: ${e.message ?: "sin conexión"}"
            }
        }
    }

    fun download() {
        val r = remote ?: return
        if (!Updater.isOnline(ctx)) {
            uState = 6
            uMsg = "Sin conexión a internet. Activa el Wi-Fi o los datos móviles."
            return
        }
        if (Updater.isWifi(ctx)) {
            startDownload()
        } else {
            val mb = if (r.size > 0) " (unos ${r.size / 1_000_000} MB)" else ""
            AlertDialog.Builder(ctx)
                .setMessage("Estás usando datos móviles. La descarga pesa$mb y gastará de tu plan. ¿Descargar ahora?")
                .setPositiveButton("Descargar") { _, _ -> startDownload() }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).clip(shape)
                .background(Brush.verticalGradient(p.dialog))
                .border(1.dp, p.dialogBorder, shape)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    GlowText("Configuración", 24.sp)
                    Text("Nioro Life", color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(p.chipBg)
                        .border(1.dp, p.chipBorder, RoundedCornerShape(14.dp)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) { CloseIcon(16.dp) }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("APARIENCIA")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in 0..1) {
                    ThemeCard(THEME_NAMES[t], paletteFor(t), settings.theme == t, Modifier.weight(1f)) { onTheme(t) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (t in 2..4) {
                    ThemeCard(THEME_NAMES[t], paletteFor(t), settings.theme == t, Modifier.weight(1f)) { onTheme(t) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Rosa, Rojo y Morado tienen su propia animación de fondo.",
                color = p.muted, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("ACTUALIZACIÓN")
            Text(
                "Versión $versionName · compilación $installed",
                color = p.body, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                uMsg,
                color = if (uState == 6) p.expense else p.muted,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            if (uState == 4) {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(p.track)
                ) {
                    Box(
                        Modifier.fillMaxHeight().fillMaxWidth(progress / 100f)
                            .clip(RoundedCornerShape(4.dp)).background(p.accent)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val sizeMb = remote?.size?.let { if (it > 0) " (${it / 1_000_000} MB)" else "" } ?: ""
            DialogButton(
                label = when (uState) {
                    1 -> "Buscando…"
                    3 -> "Descargar e instalar$sizeMb"
                    4 -> "Descargando $progress %"
                    5 -> "Instalar ahora"
                    else -> "Buscar actualización"
                },
                main = true,
                modifier = Modifier.fillMaxWidth()
            ) {
                when (uState) {
                    0, 2, 6 -> check()
                    3 -> download()
                    5 -> apk?.let { install(it) }
                    else -> Unit
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("EXPORTAR FINANZAS A LA NUBE")
            Text(
                "Crea una hoja ordenada por mes (se abre en Excel y Google Sheets) y la envías a Drive u OneDrive.",
                color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Este mes", exportScope == Export.THIS_MONTH) { exportScope = Export.THIS_MONTH; exportMsg = "" }
                Chip("Mes anterior", exportScope == Export.LAST_MONTH) { exportScope = Export.LAST_MONTH; exportMsg = "" }
                Chip("Todos", exportScope == Export.ALL_MONTHS) { exportScope = Export.ALL_MONTHS; exportMsg = "" }
            }
            Spacer(Modifier.height(12.dp))
            DialogButton("Enviar a Drive / OneDrive", true, Modifier.fillMaxWidth()) {
                val intent = try {
                    Export.shareIntent(ctx, Store.loadTx(ctx), exportScope)
                } catch (e: Exception) {
                    exportMsg = "No se pudo crear el archivo: ${e.message ?: "error"}"
                    return@DialogButton
                }
                if (intent == null) {
                    exportMsg = "No hay movimientos en ese período."
                } else {
                    try {
                        ctx.startActivity(intent)
                        exportMsg = "Elige Drive u OneDrive en la lista que se abrió."
                    } catch (e: Exception) {
                        exportMsg = "No se pudo abrir el menú de compartir."
                    }
                }
            }
            if (exportMsg.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(exportMsg, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton("Abrir Drive", false, Modifier.weight(1f)) {
                    openLink("https://drive.google.com")
                }
                DialogButton("Abrir OneDrive", false, Modifier.weight(1f)) {
                    openLink("https://onedrive.live.com")
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("ACERCA DE")
            Text(
                "Nioro Life organiza tu sueño, tus notas y tus finanzas. Todo se guarda solo en tu teléfono; solo sale lo que tú exportas.",
                color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(18.dp))
            DialogButton("Cerrar", false, Modifier.fillMaxWidth(), onDismiss)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val p = LocalPalette.current
    Text(text, color = p.muted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ThemeCard(label: String, pal: Palette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(118.dp).clip(shape)
            .background(Brush.verticalGradient(pal.bg))
            .border(if (selected) 2.5.dp else 1.dp, if (selected) p.accent else p.chipBorder, shape)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize().padding(bottom = 22.dp)) { drawMoon(pal, 0.5f) }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (selected) "$label ✓" else label,
                color = pal.title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
