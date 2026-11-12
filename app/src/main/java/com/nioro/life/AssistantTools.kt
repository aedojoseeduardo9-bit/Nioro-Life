package com.nioro.life

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** Lo que las acciones necesitan de la pantalla: confirmar, pedir permisos y moverse por la app. */
interface ToolHost {
    /** Muestra una tarjeta "¿Hago esto?" y espera la respuesta del usuario. */
    suspend fun confirm(emoji: String, title: String, detail: String, yes: String): Boolean
    /** Pide un permiso de Android si falta; devuelve si quedó concedido. */
    suspend fun permission(perm: String): Boolean
    fun navigate(screen: String)
    /** Deja una tarjeta informativa en el chat (p. ej. "Abriendo WhatsApp…"). */
    fun note(emoji: String, text: String)
}

// ---------------------------------------------------------------- texto (puro, con pruebas)

/** "Mamá  Pérez" → "mama perez": sin tildes, minúsculas y espacios simples. */
fun normalizeName(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT).replace(Regex("[^a-z0-9ñ+ ]"), " ").replace(Regex("\\s+"), " ").trim()

/** Qué tanto se parece un nombre a lo que se pidió (0 = nada). */
fun nameScore(query: String, name: String): Int {
    val q = normalizeName(query)
    val n = normalizeName(name)
    if (q.isEmpty() || n.isEmpty()) return 0
    return when {
        n == q -> 100
        n.startsWith(q) -> 80
        n.split(' ').any { it == q } -> 75
        n.contains(q) -> 60
        q.split(' ').all { w -> n.split(' ').any { it.startsWith(w) } } -> 50
        else -> 0
    }
}

/** Número para WhatsApp: solo dígitos y con código de país (Chile por defecto). */
fun waNumber(raw: String): String {
    var d = raw.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.drop(2)
    if (d.startsWith("0")) d = d.drop(1)
    return when {
        d.length == 9 && d.startsWith("9") -> "56$d"
        d.length == 8 -> "569$d"
        else -> d
    }
}

/** "18:30" o "18.30" o "18" → minutos del día; null si no se entiende. */
fun parseHhMm(s: String): Int? {
    val m = Regex("^\\s*(\\d{1,2})(?:[:.h](\\d{2}))?\\s*$").find(s) ?: return null
    val h = m.groupValues[1].toInt()
    val min = m.groupValues[2].ifEmpty { "0" }.toInt()
    return if (h in 0..23 && min in 0..59) h * 60 + min else null
}

/** "2026-11-20 18:30" → milisegundos (hora local); null si no se entiende. */
fun parseDateTime(s: String): Long? {
    val m = Regex("^\\s*(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2})[:.](\\d{2}))?\\s*$").find(s) ?: return null
    val (y, mo, d) = Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    val h = m.groupValues[4].ifEmpty { "9" }.toInt()
    val mi = m.groupValues[5].ifEmpty { "0" }.toInt()
    if (mo !in 1..12 || d !in 1..31 || h !in 0..23 || mi !in 0..59) return null
    return Calendar.getInstance().apply {
        clear()
        set(y, mo - 1, d, h, mi, 0)
    }.timeInMillis
}

/** Nombre de color → tono (0–360); null si no es un color conocido. */
fun colorHue(name: String): Int? {
    val n = normalizeName(name)
    val table = listOf(
        "rojo" to 0, "carmesi" to 350, "naranjo" to 28, "naranja" to 28, "ambar" to 40, "dorado" to 45,
        "amarillo" to 55, "lima" to 90, "verde" to 120, "esmeralda" to 145, "turquesa" to 170, "cian" to 185,
        "celeste" to 200, "azul" to 230, "indigo" to 255, "morado" to 275, "violeta" to 280, "lila" to 285,
        "purpura" to 290, "magenta" to 305, "fucsia" to 315, "rosado" to 330, "rosa" to 330
    )
    return table.firstOrNull { n.contains(it.first) }?.second
}

/** "calido" / "neutro" / "frio" → mired. */
fun whiteTemp(name: String?): Int = when (normalizeName(name ?: "")) {
    "calido", "calida" -> 440
    "frio", "fria" -> 170
    else -> 300
}

// ---------------------------------------------------------------- declaración de herramientas

private fun p(type: String, desc: String, enum: List<String>? = null, items: String? = null): JSONObject {
    val o = JSONObject().put("type", type).put("description", desc)
    if (enum != null) o.put("enum", JSONArray(enum))
    if (items != null) o.put("items", JSONObject().put("type", items))
    return o
}

private fun fn(name: String, desc: String, props: Map<String, JSONObject> = emptyMap(), required: List<String> = emptyList()): JSONObject {
    val o = JSONObject().put("name", name).put("description", desc)
    if (props.isNotEmpty()) {
        val schema = JSONObject().put("type", "object").put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        if (required.isNotEmpty()) schema.put("required", JSONArray(required))
        o.put("parameters", schema)
    }
    return o
}

/** Herramientas que el asistente puede usar (formato de "function calling" de Gemini). */
fun assistantTools(): JSONArray {
    val f = JSONArray()
    // comunicación
    f.put(fn("buscar_contacto", "Busca en los contactos del teléfono por nombre y devuelve sus números. Úsala antes de llamar o escribir a alguien por su nombre.",
        mapOf("nombre" to p("string", "Nombre o apodo, ej: mamá, Juan Pérez")), listOf("nombre")))
    f.put(fn("llamar", "Llama por teléfono a un número (se pide confirmación al usuario en pantalla).",
        mapOf("numero" to p("string", "Número a llamar"), "nombre" to p("string", "Nombre de la persona, si se sabe")), listOf("numero")))
    f.put(fn("enviar_sms", "Envía un SMS (se pide confirmación al usuario).",
        mapOf("numero" to p("string", "Número"), "texto" to p("string", "Mensaje"), "nombre" to p("string", "Nombre, si se sabe")), listOf("numero", "texto")))
    f.put(fn("enviar_whatsapp", "Abre WhatsApp con el chat y el mensaje ya escritos; el usuario solo toca enviar.",
        mapOf("numero" to p("string", "Número con o sin código de país"), "texto" to p("string", "Mensaje")), listOf("numero", "texto")))
    f.put(fn("leer_mensajes", "Lee los mensajes y correos recientes que llegaron como notificación (WhatsApp, Gmail, Mensajes, etc.). Cada uno trae un id para responder.",
        mapOf("app" to p("string", "Filtrar por app, ej: whatsapp, gmail. Vacío = todas"), "cantidad" to p("integer", "Cuántos (máx. 15)"))))
    f.put(fn("responder_mensaje", "Responde un mensaje o correo recibido usando su id de leer_mensajes (se pide confirmación).",
        mapOf("id" to p("string", "id del mensaje"), "texto" to p("string", "Respuesta")), listOf("id", "texto")))
    f.put(fn("escribir_correo", "Abre la app de correo con un correo nuevo ya escrito; el usuario lo revisa y envía.",
        mapOf("para" to p("string", "Correo del destinatario"), "asunto" to p("string", "Asunto"), "cuerpo" to p("string", "Texto del correo")), listOf("asunto", "cuerpo")))
    // teléfono
    f.put(fn("abrir_app", "Abre una app instalada por su nombre.", mapOf("nombre" to p("string", "Nombre de la app")), listOf("nombre")))
    f.put(fn("poner_alarma", "Crea una alarma en el reloj del teléfono.",
        mapOf("hora" to p("integer", "Hora 0-23"), "minuto" to p("integer", "Minuto 0-59"), "etiqueta" to p("string", "Nombre de la alarma")), listOf("hora", "minuto")))
    f.put(fn("poner_temporizador", "Inicia un temporizador.",
        mapOf("segundos" to p("integer", "Duración en segundos"), "etiqueta" to p("string", "Para qué es")), listOf("segundos")))
    f.put(fn("crear_evento", "Agrega un evento al calendario (abre el calendario para que el usuario lo guarde).",
        mapOf("titulo" to p("string", "Título"), "inicio" to p("string", "Fecha y hora, formato AAAA-MM-DD HH:MM"),
            "duracion_min" to p("integer", "Duración en minutos"), "lugar" to p("string", "Lugar")), listOf("titulo", "inicio")))
    f.put(fn("ir_a", "Abre Google Maps con la ruta hacia un lugar.", mapOf("destino" to p("string", "Dirección o lugar")), listOf("destino")))
    f.put(fn("buscar_en_internet", "Busca en Google información actual: noticias, clima, horarios, precios, resultados, datos que cambian.",
        mapOf("pregunta" to p("string", "Qué buscar")), listOf("pregunta")))
    // Nioro Life
    f.put(fn("crear_nota", "Guarda una nota en Nioro Life, con recordatorio opcional.",
        mapOf("titulo" to p("string", "Título corto"), "texto" to p("string", "Contenido"), "recordar" to p("string", "Cuándo avisar, formato AAAA-MM-DD HH:MM")), listOf("texto")))
    f.put(fn("ver_notas", "Lista las notas y recordatorios pendientes de Nioro Life."))
    f.put(fn("registrar_movimiento", "Anota un ingreso, gasto o deuda en las finanzas de Nioro Life (pesos chilenos).",
        mapOf("tipo" to p("string", "Tipo de movimiento", listOf("ingreso", "gasto", "deuda")), "monto" to p("integer", "Monto en pesos"),
            "concepto" to p("string", "Qué fue, ej: Supermercado"), "motivo" to p("string", "Detalle opcional")), listOf("tipo", "monto", "concepto")))
    f.put(fn("resumen_finanzas", "Ingresos, gastos, deudas pendientes y saldo del mes, con los últimos movimientos."))
    f.put(fn("agregar_a_lista", "Agrega cosas a una lista de Nioro Life (la crea si no existe).",
        mapOf("lista" to p("string", "Nombre de la lista, ej: Compras"), "elementos" to p("array", "Cosas a agregar", items = "string")), listOf("lista", "elementos")))
    f.put(fn("ver_listas", "Muestra las listas de Nioro Life y lo que falta en cada una."))
    f.put(fn("controlar_luces", "Controla las ampolletas y enchufes de la sección Casa.",
        mapOf(
            "accion" to p("string", "Qué hacer", listOf("encender", "apagar", "brillo", "color", "blanco", "efecto")),
            "dispositivo" to p("string", "Nombre del equipo, o 'todas'"),
            "brillo" to p("integer", "Brillo 1-100"),
            "color" to p("string", "Nombre del color, ej: azul, rosado"),
            "temperatura" to p("string", "Tipo de blanco", listOf("calido", "neutro", "frio")),
            "efecto" to p("string", "Nombre del efecto: Arcoíris, Fuego, Aurora, Océano, Respirar, Amanecer, Disco, Policía o uno propio")
        ), listOf("accion")))
    f.put(fn("ver_casa", "Lista los equipos de Casa (luces y enchufes) y si están encendidos."))
    f.put(fn("horario_sueno", "Consulta o cambia la hora de dormir y de despertar de Nioro Life.",
        mapOf("dormir" to p("string", "Nueva hora de dormir HH:MM (opcional)"), "despertar" to p("string", "Nueva hora de despertar HH:MM (opcional)"))))
    f.put(fn("resumen_entrenamiento", "Cuántos días entrenó esta semana, su meta y el último entrenamiento."))
    f.put(fn("abrir_seccion", "Abre una sección de Nioro Life.",
        mapOf("seccion" to p("string", "Sección", listOf("inicio", "reloj", "notas", "finanzas", "listas", "entreno", "casa"))), listOf("seccion")))
    return JSONArray().put(JSONObject().put("functionDeclarations", f))
}

// ---------------------------------------------------------------- ejecución

private fun ok(msg: String) = JSONObject().put("ok", true).put("resultado", msg)
private fun fail(msg: String) = JSONObject().put("ok", false).put("error", msg)

class AssistantTools(private val c: Context, private val host: ToolHost) {

    private fun has(perm: String) = c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
    private fun start(i: Intent): Boolean = try {
        c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (e: Exception) { false }

    suspend fun run(name: String, a: JSONObject): JSONObject = try {
        when (name) {
            "buscar_contacto" -> contacts(a.optString("nombre"))
            "llamar" -> call(a.optString("numero"), a.optString("nombre"))
            "enviar_sms" -> sms(a.optString("numero"), a.optString("texto"), a.optString("nombre"))
            "enviar_whatsapp" -> whatsapp(a.optString("numero"), a.optString("texto"))
            "leer_mensajes" -> readMessages(a.optString("app"), a.optInt("cantidad", 8))
            "responder_mensaje" -> replyMessage(a.optString("id"), a.optString("texto"))
            "escribir_correo" -> email(a.optString("para"), a.optString("asunto"), a.optString("cuerpo"))
            "abrir_app" -> openApp(a.optString("nombre"))
            "poner_alarma" -> alarm(a.optInt("hora", -1), a.optInt("minuto", 0), a.optString("etiqueta"))
            "poner_temporizador" -> timer(a.optInt("segundos", 0), a.optString("etiqueta"))
            "crear_evento" -> event(a.optString("titulo"), a.optString("inicio"), a.optInt("duracion_min", 60), a.optString("lugar"))
            "ir_a" -> navigateTo(a.optString("destino"))
            "buscar_en_internet" -> ok(GeminiClient.searchWeb(c, a.optString("pregunta")))
            "crear_nota" -> note(a.optString("titulo"), a.optString("texto"), a.optString("recordar"))
            "ver_notas" -> listNotes()
            "registrar_movimiento" -> money(a.optString("tipo"), a.optLong("monto", 0), a.optString("concepto"), a.optString("motivo"))
            "resumen_finanzas" -> financeSummary()
            "agregar_a_lista" -> addToList(a.optString("lista"), a.optJSONArray("elementos"))
            "ver_listas" -> showLists()
            "controlar_luces" -> lights(a)
            "ver_casa" -> showHome()
            "horario_sueno" -> sleep(a.optString("dormir"), a.optString("despertar"))
            "resumen_entrenamiento" -> training()
            "abrir_seccion" -> section(a.optString("seccion"))
            else -> fail("No conozco la acción $name")
        }
    } catch (e: SecurityException) {
        fail("Falta un permiso de Android para hacer eso.")
    } catch (e: Exception) {
        fail(e.message ?: "No se pudo hacer.")
    }

    // ---- comunicación

    private suspend fun contacts(query: String): JSONObject {
        if (!host.permission(Manifest.permission.READ_CONTACTS)) return fail("El usuario no dio permiso para ver sus contactos.")
        val found = withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, MutableSet<String>>()
            c.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null
            )?.use { cur ->
                while (cur.moveToNext()) {
                    val n = cur.getString(0) ?: continue
                    val num = cur.getString(1) ?: continue
                    map.getOrPut(n) { linkedSetOf() }.add(num.replace(" ", ""))
                }
            }
            map.entries.map { it to nameScore(query, it.key) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        }
        if (found.isEmpty()) return fail("No encontré a \"$query\" en los contactos.")
        val arr = JSONArray()
        found.forEach { (e, _) -> arr.put(JSONObject().put("nombre", e.key).put("numeros", JSONArray(e.value.toList()))) }
        return JSONObject().put("ok", true).put("contactos", arr)
    }

    private suspend fun call(number: String, who: String): JSONObject {
        if (number.count { it.isDigit() } < 3) return fail("Número no válido.")
        val label = if (who.isNotBlank()) "$who ($number)" else number
        if (!host.confirm("📞", "¿Llamar a ${who.ifBlank { number }}?", label, "Llamar")) return fail("El usuario canceló la llamada.")
        val uri = Uri.fromParts("tel", number, null)
        return if (host.permission(Manifest.permission.CALL_PHONE) && start(Intent(Intent.ACTION_CALL, uri))) ok("Llamando a $label.")
        else if (start(Intent(Intent.ACTION_DIAL, uri))) ok("Abrí el teclado con el número; el usuario debe tocar llamar.")
        else fail("No pude abrir el teléfono.")
    }

    private suspend fun sms(number: String, text: String, who: String): JSONObject {
        if (text.isBlank()) return fail("El mensaje está vacío.")
        if (!host.confirm("💬", "¿Enviar SMS a ${who.ifBlank { number }}?", text, "Enviar")) return fail("El usuario canceló el SMS.")
        if (host.permission(Manifest.permission.SEND_SMS)) {
            @Suppress("DEPRECATION")
            val sm = if (android.os.Build.VERSION.SDK_INT >= 31) c.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            if (sm != null) {
                sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
                return ok("SMS enviado a ${who.ifBlank { number }}.")
            }
        }
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).putExtra("sms_body", text)
        return if (start(i)) ok("Abrí Mensajes con el texto listo; el usuario debe tocar enviar.") else fail("No pude abrir Mensajes.")
    }

    private fun whatsapp(number: String, text: String): JSONObject {
        val n = waNumber(number)
        if (n.length < 8) return fail("Número no válido para WhatsApp.")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$n&text=" + URLEncoder.encode(text, "UTF-8"))
        host.note("🟢", "Abriendo WhatsApp…")
        return if (start(Intent(Intent.ACTION_VIEW, uri))) ok("WhatsApp abierto con el mensaje escrito; el usuario debe tocar enviar.")
        else fail("No pude abrir WhatsApp. ¿Está instalado?")
    }

    private fun readMessages(app: String, count: Int): JSONObject {
        if (!NotiListener.enabled(c)) {
            return fail("Nioro Life no tiene 'Acceso a notificaciones'. Dile al usuario que lo active en los ajustes del asistente (botón Permisos).")
        }
        val list = NotiListener.messages(app.ifBlank { null }, count.coerceIn(1, 15))
        if (list.isEmpty()) return ok("No hay mensajes nuevos${if (app.isNotBlank()) " de $app" else ""} desde que se abrió Nioro Life.")
        val arr = JSONArray()
        val now = System.currentTimeMillis()
        list.forEach { m ->
            arr.put(JSONObject().put("id", m.id).put("app", m.app).put("de", m.from).put("texto", m.text)
                .put("hace_min", (now - m.time) / 60_000).put("se_puede_responder", m.canReply))
        }
        return JSONObject().put("ok", true).put("mensajes", arr)
    }

    private suspend fun replyMessage(id: String, text: String): JSONObject {
        val m = NotiListener.messages(null, 60).firstOrNull { it.id == id } ?: return fail("No encontré ese mensaje; vuelve a leer los mensajes.")
        if (!m.canReply) return fail("Ese mensaje no se puede responder desde la notificación.")
        if (!host.confirm("↩️", "¿Responder a ${m.from} (${m.app})?", text, "Responder")) return fail("El usuario canceló la respuesta.")
        return if (NotiListener.reply(c, id, text)) ok("Respuesta enviada a ${m.from}.") else fail("La notificación ya no está disponible; abre la app para responder.")
    }

    private fun email(to: String, subject: String, body: String): JSONObject {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
        if (to.isNotBlank()) i.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        host.note("✉️", "Abriendo el correo…")
        return if (start(i)) ok("Correo abierto y escrito; el usuario debe revisarlo y tocar enviar.") else fail("No hay app de correo.")
    }

    // ---- teléfono

    private fun openApp(name: String): JSONObject {
        val pm = c.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val best = apps.map { it to nameScore(name, it.loadLabel(pm).toString()) }.filter { it.second > 0 }.maxByOrNull { it.second }
            ?: return fail("No encontré una app llamada \"$name\".")
        val launch = pm.getLaunchIntentForPackage(best.first.activityInfo.packageName) ?: return fail("No se puede abrir esa app.")
        return if (start(launch)) ok("Abrí ${best.first.loadLabel(pm)}.") else fail("No se pudo abrir.")
    }

    private fun alarm(h: Int, m: Int, label: String): JSONObject {
        if (h !in 0..23 || m !in 0..59) return fail("Hora no válida.")
        val i = Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (start(i)) ok("Alarma puesta a las ${fmtTime(h * 60 + m)}.") else fail("No pude poner la alarma.")
    }

    private fun timer(sec: Int, label: String): JSONObject {
        if (sec !in 1..86_400) return fail("Duración no válida.")
        val i = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (start(i)) ok("Temporizador de ${sec / 60} min ${sec % 60} s iniciado.") else fail("No pude iniciar el temporizador.")
    }

    private fun event(title: String, startText: String, durMin: Int, place: String): JSONObject {
        val begin = parseDateTime(startText) ?: return fail("Fecha no válida; usa AAAA-MM-DD HH:MM.")
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + durMin.coerceIn(5, 1440) * 60_000L)
            .putExtra(CalendarContract.Events.TITLE, title)
        if (place.isNotBlank()) i.putExtra(CalendarContract.Events.EVENT_LOCATION, place)
        return if (start(i)) ok("Abrí el calendario con el evento listo; el usuario debe tocar guardar.") else fail("No hay app de calendario.")
    }

    private fun navigateTo(dest: String): JSONObject {
        val q = URLEncoder.encode(dest, "UTF-8")
        return if (start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$q"))) ||
            start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))) ok("Ruta a $dest abierta en el mapa.")
        else fail("No hay app de mapas.")
    }

    // ---- Nioro Life

    private fun note(title: String, text: String, remind: String): JSONObject {
        val at = remind.takeIf { it.isNotBlank() }?.let { parseDateTime(it) }
        if (remind.isNotBlank() && at == null) return fail("La fecha del recordatorio no es válida; usa AAAA-MM-DD HH:MM.")
        if (at != null && at <= System.currentTimeMillis()) return fail("Esa hora ya pasó.")
        val notes = Store.loadNotes(c)
        Store.saveNotes(c, notes + Note(System.currentTimeMillis(), title.trim(), text.trim(), at, false))
        Scheduler.scheduleAll(c)
        return ok("Nota guardada" + (at?.let { " con recordatorio para ${fmtDateShort(it)}" } ?: "") + ".")
    }

    private fun listNotes(): JSONObject {
        val now = System.currentTimeMillis()
        val arr = JSONArray()
        Store.loadNotes(c).filter { !it.done }.sortedBy { it.remindAt ?: Long.MAX_VALUE }.take(15).forEach { n ->
            arr.put(JSONObject().put("titulo", n.title).put("texto", n.text.take(200))
                .put("recordatorio", n.remindAt?.takeIf { it > now }?.let { fmtDateShort(it) } ?: ""))
        }
        return JSONObject().put("ok", true).put("notas", arr)
    }

    private fun money(type: String, amount: Long, concept: String, reason: String): JSONObject {
        val kind = when (normalizeName(type)) { "ingreso" -> 0; "deuda" -> 2; else -> 1 }
        if (amount <= 0) return fail("El monto debe ser mayor que cero.")
        val tx = Store.loadTx(c)
        Store.saveTx(c, tx + Tx(System.currentTimeMillis(), kind, amount, concept.trim(), reason.trim(), System.currentTimeMillis(), false))
        val s = monthSummary(Store.loadTx(c), System.currentTimeMillis())
        return ok("${listOf("Ingreso", "Gasto", "Deuda")[kind]} de ${fmtMoney(amount)} anotado. Saldo del mes: ${fmtMoney(s.balance)}.")
    }

    private fun financeSummary(): JSONObject {
        val now = System.currentTimeMillis()
        val tx = Store.loadTx(c)
        val s = monthSummary(tx, now)
        val last = JSONArray()
        tx.filter { sameMonth(it.date, now) }.sortedByDescending { it.date }.take(8).forEach {
            last.put(JSONObject().put("tipo", listOf("ingreso", "gasto", "deuda")[it.kind.coerceIn(0, 2)]).put("monto", it.amount).put("concepto", it.title))
        }
        val owed = Store.loadCobros(c).filter { !it.settled }.sumOf { it.remaining }
        return JSONObject().put("ok", true).put("ingresos", s.income).put("gastos", s.expenses).put("deudas_pendientes", s.pending)
            .put("saldo", s.balance).put("por_cobrar", owed).put("ultimos", last).put("moneda", "CLP")
    }

    private fun addToList(listName: String, items: JSONArray?): JSONObject {
        val things = (0 until (items?.length() ?: 0)).mapNotNull { items?.optString(it)?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (things.isEmpty()) return fail("No hay elementos para agregar.")
        val lists = Store.loadLists(c)
        val target = lists.map { it to nameScore(listName, it.title) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
        val now = System.currentTimeMillis()
        val newItems = things.mapIndexed { i, t -> CheckItem(now + i, t.replaceFirstChar { it.uppercase() }, 0, false) }
        var hist = Store.loadHistory(c)
        val result = if (target != null) {
            val existing = target.items.map { normalizeName(it.text) }.toSet()
            val add = newItems.filter { normalizeName(it.text) !in existing }
            Store.saveLists(c, lists.map { if (it.id == target.id) it.copy(items = it.items + add) else it })
            add.forEach { hist = recordHistory(hist, target.kind, it.text, 0) }
            "Agregué ${add.size} a \"${target.title}\"" + if (add.size < newItems.size) " (el resto ya estaba)." else "."
        } else {
            val kind = LIST_KIND_NAMES.indexOfFirst { nameScore(listName, it) >= 60 }.takeIf { it >= 0 } ?: 4
            val title = listName.trim().replaceFirstChar { it.uppercase() }.ifBlank { LIST_KIND_NAMES[kind] }
            Store.saveLists(c, lists + CheckList(now, title, kind, newItems, now))
            newItems.forEach { hist = recordHistory(hist, kind, it.text, 0) }
            "Creé la lista \"$title\" con ${newItems.size} cosas."
        }
        Store.saveHistory(c, hist)
        return ok(result)
    }

    private fun showLists(): JSONObject {
        val arr = JSONArray()
        Store.loadLists(c).forEach { l ->
            arr.put(JSONObject().put("lista", l.title).put("faltan", JSONArray(l.items.filter { !it.done }.map { it.text })).put("hechas", l.items.count { it.done }))
        }
        return JSONObject().put("ok", true).put("listas", arr)
    }

    private data class HomeDev(val name: String, val local: SmartDevice?, val tuya: TuyaDevice?)

    private suspend fun homeDevices(fresh: Boolean): List<HomeDev> {
        val tuya = if (TuyaCloud.cfg(c) == null) emptyList()
        else if (fresh) try { TuyaCloud.devices(c) } catch (e: Exception) { TuyaCloud.cached(c) } else TuyaCloud.cached(c)
        return SmartHome.load(c).map { HomeDev(it.name, it, null) } + tuya.map { HomeDev(it.name, null, it) }
    }

    private suspend fun showHome(): JSONObject {
        val devs = homeDevices(fresh = true)
        if (devs.isEmpty()) return ok("No hay equipos en Casa. Se agregan en la sección Casa (Smart Life o ampolletas liberadas).")
        val arr = JSONArray()
        devs.forEach { d ->
            val st = d.tuya?.let { tuyaState(it) } ?: d.local?.let { SmartHome.refresh(it.ip, BulbState()) } ?: BulbState()
            arr.put(JSONObject().put("nombre", d.name).put("conectado", st.online).put("encendido", st.on).put("brillo", st.dimmer))
        }
        return JSONObject().put("ok", true).put("equipos", arr)
    }

    private suspend fun lights(a: JSONObject): JSONObject {
        var devs = homeDevices(fresh = false)
        if (devs.isEmpty()) devs = homeDevices(fresh = true)
        if (devs.isEmpty()) return fail("No hay luces en Casa todavía.")
        val which = a.optString("dispositivo")
        val targets = if (which.isBlank() || normalizeName(which) in setOf("todas", "todo", "todos", "casa")) devs
        else devs.map { it to nameScore(which, it.name) }.filter { it.second > 0 }.sortedByDescending { it.second }
            .let { s -> if (s.isEmpty()) emptyList() else s.filter { it.second == s[0].second }.map { it.first } }
        if (targets.isEmpty()) return fail("No encontré \"$which\". Equipos: ${devs.joinToString { it.name }}.")
        val pct = a.optInt("brillo", -1).takeIf { it in 1..100 }
        val cmd: LightCmd = when (normalizeName(a.optString("accion"))) {
            "encender" -> if (pct != null) LightCmd.Level(pct) else LightCmd.Power(true)
            "apagar" -> LightCmd.Power(false)
            "brillo" -> LightCmd.Level(pct ?: 60)
            "color" -> {
                val hue = colorHue(a.optString("color")) ?: return fail("No conozco el color \"${a.optString("color")}\".")
                LightCmd.Hsv(hue, 100, pct ?: 100)
            }
            "blanco" -> LightCmd.White(whiteTemp(a.optString("temperatura")), pct ?: 100)
            "efecto" -> {
                val all = LIGHT_EFFECTS + TuyaCloud.loadEffects(c)
                val fx = all.map { it to nameScore(a.optString("efecto"), it.name) }.filter { it.second > 0 }.maxByOrNull { it.second }?.first
                    ?: return fail("Efectos disponibles: ${all.joinToString { it.name }}.")
                LightCmd.Fx(fx)
            }
            else -> return fail("Acción no válida.")
        }
        val done = mutableListOf<String>()
        val errors = mutableListOf<String>()
        for (d in targets) {
            try {
                if (d.local != null) {
                    val lc = localCmd(cmd)
                    if (lc == null) { errors += "${d.name}: no tiene efectos"; continue }
                    val r = SmartHome.send(d.local.ip, lc, BulbState())
                    if (r.online) done += d.name else errors += "${d.name}: sin conexión"
                } else if (d.tuya != null) {
                    if (cmd is LightCmd.Fx && !tuyaCaps(d.tuya).effects) { errors += "${d.name}: no tiene efectos"; continue }
                    TuyaCloud.command(c, d.tuya.id, tuyaCommands(d.tuya, cmd, tuyaState(d.tuya)))
                    done += d.name
                }
            } catch (e: Exception) {
                errors += "${d.name}: ${e.message ?: "error"}"
            }
        }
        if (done.isEmpty()) return fail(errors.joinToString("; "))
        return ok("Listo: ${done.joinToString()}" + if (errors.isNotEmpty()) ". Fallaron: ${errors.joinToString("; ")}" else ".")
    }

    private fun sleep(bed: String, wake: String): JSONObject {
        var s = Store.loadSettings(c)
        if (bed.isNotBlank()) s = s.copy(bedMin = parseHhMm(bed) ?: return fail("Hora de dormir no válida."))
        if (wake.isNotBlank()) s = s.copy(wakeMin = parseHhMm(wake) ?: return fail("Hora de despertar no válida."))
        if (bed.isNotBlank() || wake.isNotBlank()) { Store.saveSettings(c, s); Scheduler.scheduleAll(c) }
        return ok("Duerme a las ${fmtTime(s.bedMin)} y despierta a las ${fmtTime(s.wakeMin)} (${fmtDur(sleepDurMin(s))}). " +
            "Alarma de despertar: ${if (s.alarmOn) "activada" else "apagada"}.")
    }

    private fun training(): JSONObject {
        val h = TrainStore.loadHistory(c)
        val cfg = TrainStore.loadCfg(c)
        val now = System.currentTimeMillis()
        val last = h.maxByOrNull { it.start }
        return JSONObject().put("ok", true).put("dias_esta_semana", daysThisWeek(h, now)).put("meta_semanal", cfg.weeklyGoal)
            .put("racha_semanas", weekStreak(h, cfg.weeklyGoal, now)).put("total_entrenamientos", h.size)
            .put("ultimo", last?.let { "${it.name}, ${fmtDateShort(it.start)}, ${workoutDoneSets(it)} series" } ?: "ninguno")
    }

    private fun section(s: String): JSONObject {
        val screen = mapOf("inicio" to "home", "reloj" to "clock", "notas" to "notes", "finanzas" to "finance",
            "listas" to "lists", "entreno" to "train", "casa" to "casa")[normalizeName(s)] ?: return fail("Sección no válida.")
        host.navigate(screen)
        return ok("Abriendo $s.")
    }
}

fun fmtDateShort(ms: Long): String =
    java.text.SimpleDateFormat("EEE d MMM, HH:mm", Locale("es", "CL")).format(java.util.Date(ms))
