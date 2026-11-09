package com.nioro.life

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

/**
 * Puente con Smart Life: Nioro Life habla con la nube oficial de Tuya (Tuya OpenAPI) usando un
 * "proyecto en la nube" gratuito que el usuario enlaza a su cuenta de Smart Life escaneando un QR.
 * Así ve y controla los mismos equipos que Smart Life, sin abrir Smart Life.
 */

/** Centros de datos de Tuya. La cuenta de Smart Life y el proyecto deben estar en el mismo. */
val TUYA_REGIONS = listOf(
    "América (Oeste)" to "https://openapi.tuyaus.com",
    "América (Este)" to "https://openapi-ueaz.tuyaus.com",
    "Europa (Central)" to "https://openapi.tuyaeu.com",
    "Europa (Oeste)" to "https://openapi-weaz.tuyaeu.com",
    "India" to "https://openapi.tuyain.com",
    "China" to "https://openapi.tuyacn.com"
)

data class TuyaCfg(val accessId: String, val secret: String, val endpoint: String)

/** Un equipo de la cuenta de Smart Life. `status`: código → valor (Boolean, Int o String). */
data class TuyaDevice(
    val id: String,
    val name: String,
    val category: String,
    val online: Boolean,
    val product: String,
    val status: Map<String, Any?>
)

/** Qué sabe hacer un equipo; decide qué controles se muestran. */
data class LightCaps(
    val light: Boolean = true,
    val dimmer: Boolean = true,
    val color: Boolean = true,
    val white: Boolean = true,
    val effects: Boolean = false,
    val timer: Boolean = false
)

/** Una orden para una luz, independiente de si va por la red local o por Smart Life. */
sealed interface LightCmd {
    data class Power(val on: Boolean) : LightCmd
    /** Brillo 1–100 manteniendo el modo actual. */
    data class Level(val pct: Int) : LightCmd
    /** Color: tono 0–360, saturación 0–100, brillo 1–100. */
    data class Hsv(val h: Int, val s: Int, val v: Int) : LightCmd
    /** Blanco: temperatura en mired (153 frío – 500 cálido) y brillo 1–100. */
    data class White(val ct: Int, val pct: Int) : LightCmd
    data class Fx(val fx: LightEffect) : LightCmd
    /** Apagar dentro de `sec` segundos (0 = cancelar). */
    data class Timer(val sec: Int) : LightCmd
}

/** Efecto animado: la ampolleta recorre estos colores sola (escena de Tuya). */
data class LightEffect(
    val name: String,
    val emoji: String,
    /** "gradient" (suave), "jump" (saltos) o "static" */
    val mode: String,
    /** 1 (lento) – 100 (rápido) */
    val speed: Int,
    /** (tono 0–360, saturación 0–100, brillo 1–100) */
    val colors: List<Triple<Int, Int, Int>>,
    val custom: Boolean = false
)

val LIGHT_EFFECTS = listOf(
    LightEffect("Arcoíris", "🌈", "gradient", 45, listOf(0, 60, 120, 180, 240, 300).map { Triple(it, 100, 100) }),
    LightEffect("Fuego", "🔥", "gradient", 85, listOf(Triple(8, 100, 100), Triple(25, 100, 70), Triple(15, 100, 90), Triple(35, 95, 60))),
    LightEffect("Aurora", "🌌", "gradient", 20, listOf(Triple(140, 90, 90), Triple(175, 85, 90), Triple(265, 75, 85), Triple(200, 80, 80))),
    LightEffect("Océano", "🌊", "gradient", 30, listOf(Triple(195, 90, 90), Triple(220, 85, 70), Triple(180, 70, 90))),
    LightEffect("Respirar", "💓", "gradient", 35, listOf(Triple(330, 75, 100), Triple(330, 75, 8))),
    LightEffect("Amanecer", "🌅", "gradient", 10, listOf(Triple(5, 100, 20), Triple(20, 100, 60), Triple(35, 80, 100))),
    LightEffect("Disco", "🪩", "jump", 80, listOf(0, 60, 120, 180, 240, 300).map { Triple(it, 100, 100) }),
    LightEffect("Policía", "🚨", "jump", 95, listOf(Triple(0, 100, 100), Triple(230, 100, 100)))
)

// ---------------------------------------------------------------- cálculos puros (con pruebas)

fun sha256Hex(s: String): String =
    MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

/** URL con los parámetros ordenados alfabéticamente, como pide la firma de Tuya. */
fun tuyaUrl(path: String, params: Map<String, String> = emptyMap()): String =
    if (params.isEmpty()) path else path + "?" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

/** Texto a firmar: método, hash del cuerpo, cabeceras firmadas (ninguna) y URL. */
fun tuyaStringToSign(method: String, body: String, url: String): String =
    method + "\n" + sha256Hex(body) + "\n" + "\n" + url

/** Firma HMAC-SHA256 en mayúsculas. `token` vacío para pedir el token. */
fun tuyaSign(clientId: String, secret: String, token: String, t: String, nonce: String, stringToSign: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal((clientId + token + t + nonce + stringToSign).toByteArray()).joinToString("") { "%02X".format(it) }
}

/** JSON mínimo para los comandos (mapas, listas, texto, números y verdadero/falso). */
fun toJson(v: Any?): String = when (v) {
    null -> "null"
    is String -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    is Boolean, is Int, is Long -> v.toString()
    is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    is Map<*, *> -> v.entries.joinToString(",", "{", "}") { toJson(it.key.toString()) + ":" + toJson(it.value) }
    is List<*> -> v.joinToString(",", "[", "]") { toJson(it) }
    else -> toJson(v.toString())
}

private fun firstCode(st: Map<String, Any?>, vararg codes: String): String? = codes.firstOrNull { st.containsKey(it) }

fun tuyaPowerCode(st: Map<String, Any?>) = firstCode(st, "switch_led", "switch_led_1", "switch_1", "switch")
private fun brightCode(st: Map<String, Any?>) = firstCode(st, "bright_value_v2", "bright_value", "bright_value_1")
private fun tempCode(st: Map<String, Any?>) = firstCode(st, "temp_value_v2", "temp_value")
private fun colourCode(st: Map<String, Any?>) = firstCode(st, "colour_data_v2", "colour_data")
private fun timerCode(st: Map<String, Any?>) = firstCode(st, "countdown_1", "countdown")

/** Lo que sabe hacer el equipo, según los códigos que informa. */
fun tuyaCaps(d: TuyaDevice): LightCaps {
    val st = d.status
    val color = colourCode(st) != null
    return LightCaps(
        light = brightCode(st) != null || color || d.category in setOf("dj", "dd", "xdd", "fwd", "dc", "gyd", "tyndj"),
        dimmer = brightCode(st) != null || color,
        color = color,
        white = tempCode(st) != null,
        effects = st.containsKey("scene_data_v2") && st.containsKey("work_mode"),
        timer = timerCode(st) != null
    )
}

private fun intOf(v: Any?): Int? = when (v) {
    is Int -> v
    is Long -> v.toInt()
    is Double -> v.roundToInt()
    is String -> v.toIntOrNull()
    else -> null
}

/** Lee {"h":120,"s":1000,"v":500} aunque venga como texto. */
fun parseTuyaHsv(v: Any?): Triple<Int, Int, Int>? {
    val s = v?.toString() ?: return null
    fun get(k: String) = Regex("\"$k\"\\s*:\\s*(-?\\d+)").find(s)?.groupValues?.get(1)?.toIntOrNull()
    return Triple(get("h") ?: return null, get("s") ?: return null, get("v") ?: return null)
}

/** Estado para la pantalla a partir de los códigos de Tuya. */
fun tuyaState(d: TuyaDevice): BulbState {
    val st = d.status
    val pc = tuyaPowerCode(st)
    val on = pc != null && st[pc] == true
    val mode = st["work_mode"]?.toString() ?: "white"
    val colour = parseTuyaHsv(colourCode(st)?.let { st[it] })
    val bc = brightCode(st)
    val bright = bc?.let { intOf(st[it]) }
    val temp = tempCode(st)?.let { intOf(st[it]) }
    val inColour = mode == "colour" || (bc == null && colour != null)
    val sMax = if (st.containsKey("colour_data_v2")) 1000 else 255
    val pct = when {
        inColour && colour != null -> colour.third * 100 / sMax
        bright != null && bc == "bright_value" -> (bright - 25) * 99 / 230 + 1
        bright != null -> bright / 10
        else -> 100
    }.coerceIn(1, 100)
    val tMax = if (st.containsKey("temp_value_v2")) 1000 else 255
    return BulbState(
        online = d.online,
        on = on,
        dimmer = pct,
        hue = colour?.first?.coerceIn(0, 360) ?: 40,
        sat = colour?.let { it.second * 100 / sMax }?.coerceIn(0, 100) ?: 0,
        ct = temp?.let { 500 - it * 347 / tMax }?.coerceIn(153, 500) ?: 300,
        white = !inColour,
        scene = mode.startsWith("scene")
    )
}

/** Escena de Tuya (scene_data_v2) para un efecto. */
fun tuyaScene(fx: LightEffect): Map<String, Any> {
    val dur = (101 - fx.speed.coerceIn(1, 100)).coerceIn(1, 100)
    return mapOf(
        "scene_num" to 1,
        "scene_units" to fx.colors.map { (h, s, v) ->
            mapOf(
                "bright" to 0, "h" to h, "s" to s * 10, "temperature" to 0, "v" to v.coerceIn(1, 100) * 10,
                "unit_change_mode" to fx.mode, "unit_gradient_duration" to dur, "unit_switch_duration" to dur
            )
        }
    )
}

/** Traduce una orden a los códigos de Tuya del equipo. */
fun tuyaCommands(d: TuyaDevice, cmd: LightCmd, cur: BulbState): List<Pair<String, Any>> {
    val st = d.status
    val pc = tuyaPowerCode(st)
    val bc = brightCode(st)
    val tc = tempCode(st)
    val cc = colourCode(st)
    val hasMode = st.containsKey("work_mode")
    val v2Colour = cc == "colour_data_v2"
    fun bright(pct: Int): Int = when (bc) {
        "bright_value" -> 25 + (pct.coerceIn(1, 100) - 1) * 230 / 99
        else -> pct.coerceIn(1, 100) * 10
    }
    fun colour(h: Int, s: Int, v: Int): Map<String, Int> {
        val m = if (v2Colour) 1000 else 255
        return mapOf("h" to h.coerceIn(0, 360), "s" to s.coerceIn(0, 100) * m / 100, "v" to v.coerceIn(1, 100) * m / 100)
    }
    val turnOn = if (pc != null && !cur.on) listOf(pc to true) else emptyList()
    return when (cmd) {
        is LightCmd.Power -> listOfNotNull(pc?.let { it to cmd.on })
        is LightCmd.Level -> turnOn + when {
            !cur.white && cc != null -> listOf(cc to colour(cur.hue, cur.sat, cmd.pct))
            bc != null -> listOf(bc to bright(cmd.pct))
            else -> emptyList()
        }
        is LightCmd.Hsv -> turnOn + (if (hasMode) listOf("work_mode" to "colour") else emptyList()) +
            listOfNotNull(cc?.let { it to colour(cmd.h, cmd.s, cmd.v) })
        is LightCmd.White -> {
            val tMax = if (tc == "temp_value_v2") 1000 else 255
            turnOn + (if (hasMode) listOf("work_mode" to "white") else emptyList()) +
                listOfNotNull(tc?.let { it to ((500 - cmd.ct.coerceIn(153, 500)) * tMax / 347) }) +
                listOfNotNull(bc?.let { it to bright(cmd.pct) })
        }
        is LightCmd.Fx -> turnOn + listOf("work_mode" to "scene", "scene_data_v2" to tuyaScene(cmd.fx))
        is LightCmd.Timer -> listOfNotNull(timerCode(st)?.let { it to cmd.sec.coerceIn(0, 86400) })
    }
}

/** Mensaje claro para los errores más comunes de Tuya. */
fun tuyaError(code: Int, msg: String): String = when (code) {
    1004 -> "La firma no es válida: revisa el Access Secret."
    1010, 1011 -> "La sesión con Tuya venció; vuelve a intentar."
    1106, 28841105 -> "El proyecto no tiene permiso: en iot.tuya.com activa el servicio \"IoT Core\" del proyecto."
    28841002, 28841101 -> "Tu plan gratuito de Tuya venció: en iot.tuya.com → Cloud → IoT Core, pide la extensión (es gratis)."
    2009, 1005 -> "El Access ID no existe en esa región. Revisa el Access ID o prueba otra región."
    else -> "Tuya respondió: $msg ($code)"
}

// ---------------------------------------------------------------- red

object TuyaCloud {
    private fun prefs(c: Context) = c.getSharedPreferences("nioro_tuya", Context.MODE_PRIVATE)

    fun cfg(c: Context): TuyaCfg? {
        val p = prefs(c)
        val id = p.getString("id", "") ?: ""
        val sec = p.getString("secret", "") ?: ""
        if (id.isBlank() || sec.isBlank()) return null
        return TuyaCfg(id, sec, p.getString("endpoint", TUYA_REGIONS[0].second) ?: TUYA_REGIONS[0].second)
    }

    fun saveCfg(c: Context, cfg: TuyaCfg?) {
        val e = prefs(c).edit().remove("token").remove("tokenExp")
        if (cfg == null) e.remove("id").remove("secret").remove("devices")
        else e.putString("id", cfg.accessId.trim()).putString("secret", cfg.secret.trim()).putString("endpoint", cfg.endpoint)
        e.apply()
    }

    class TuyaException(msg: String) : IOException(msg)

    private fun http(cfg: TuyaCfg, method: String, path: String, params: Map<String, String>, body: String, token: String): JSONObject {
        val url = tuyaUrl(path, params)
        val t = System.currentTimeMillis().toString()
        val sign = tuyaSign(cfg.accessId, cfg.secret, token, t, "", tuyaStringToSign(method, body, url))
        val conn = URL(cfg.endpoint + url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = method
        conn.setRequestProperty("client_id", cfg.accessId)
        conn.setRequestProperty("sign", sign)
        conn.setRequestProperty("t", t)
        conn.setRequestProperty("sign_method", "HMAC-SHA256")
        conn.setRequestProperty("nonce", "")
        if (token.isNotEmpty()) conn.setRequestProperty("access_token", token)
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            if (body.isNotEmpty()) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: throw TuyaException("Tuya no respondió (${conn.responseCode})")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun token(c: Context, cfg: TuyaCfg, force: Boolean = false): String {
        val p = prefs(c)
        val cached = p.getString("token", null)
        if (!force && cached != null && System.currentTimeMillis() < p.getLong("tokenExp", 0)) return cached
        val r = http(cfg, "GET", "/v1.0/token", mapOf("grant_type" to "1"), "", "")
        if (!r.optBoolean("success")) throw TuyaException(tuyaError(r.optInt("code"), r.optString("msg")))
        val res = r.getJSONObject("result")
        val tk = res.getString("access_token")
        p.edit().putString("token", tk)
            .putLong("tokenExp", System.currentTimeMillis() + (res.optLong("expire_time", 7200) - 120) * 1000).apply()
        return tk
    }

    /** Llamada con token; si la sesión venció, pide otra una vez. */
    private fun call(c: Context, method: String, path: String, params: Map<String, String> = emptyMap(), body: String = ""): JSONObject {
        val cfg = cfg(c) ?: throw TuyaException("Smart Life no está enlazado")
        var r = http(cfg, method, path, params, body, token(c, cfg))
        if (!r.optBoolean("success") && r.optInt("code") in setOf(1010, 1011)) {
            r = http(cfg, method, path, params, body, token(c, cfg, force = true))
        }
        if (!r.optBoolean("success")) throw TuyaException(tuyaError(r.optInt("code"), r.optString("msg")))
        return r
    }

    private fun statusMap(a: JSONArray?): Map<String, Any?> {
        if (a == null) return emptyMap()
        val m = LinkedHashMap<String, Any?>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val v = o.opt("value")
            m[o.optString("code")] = when (v) {
                is JSONObject, is JSONArray -> v.toString()
                JSONObject.NULL -> null
                else -> v
            }
        }
        return m
    }

    private fun deviceFrom(o: JSONObject, status: Map<String, Any?>? = null) = TuyaDevice(
        id = o.optString("id"),
        name = o.optString("name").ifBlank { o.optString("product_name", "Equipo") },
        category = o.optString("category"),
        online = o.optBoolean("online", o.optBoolean("is_online", true)),
        product = o.optString("product_name"),
        status = status ?: statusMap(o.optJSONArray("status"))
    )

    /** Todos los equipos de la cuenta de Smart Life enlazada, con su estado. */
    suspend fun devices(c: Context): List<TuyaDevice> = withContext(Dispatchers.IO) {
        val out = mutableListOf<TuyaDevice>()
        try {
            var last = ""
            do {
                val params = if (last.isEmpty()) mapOf("size" to "100") else mapOf("size" to "100", "last_row_key" to last)
                val res = call(c, "GET", "/v1.0/iot-01/associated-users/devices", params).getJSONObject("result")
                val arr = res.optJSONArray("devices") ?: JSONArray()
                for (i in 0 until arr.length()) out += deviceFrom(arr.getJSONObject(i))
                last = res.optString("last_row_key", "")
            } while (res.optBoolean("has_more") && last.isNotEmpty())
        } catch (e: TuyaException) {
            // otra forma de listar (proyectos nuevos): lista + estado por separado
            if (out.isNotEmpty()) throw e
            val res = call(c, "GET", "/v2.0/cloud/thing/device", mapOf("page_size" to "20"))
            val arr = res.optJSONArray("result") ?: throw e
            val list = (0 until arr.length()).map { arr.getJSONObject(it) }
            list.forEach { o ->
                val st = try { statusMap(call(c, "GET", "/v1.0/devices/${o.optString("id")}/status").optJSONArray("result")) } catch (x: Exception) { emptyMap() }
                out += deviceFrom(o, st)
            }
        }
        saveCache(c, out)
        out
    }

    /** Envía órdenes a un equipo. */
    suspend fun command(c: Context, id: String, cmds: List<Pair<String, Any>>) = withContext(Dispatchers.IO) {
        if (cmds.isEmpty()) return@withContext
        val body = toJson(mapOf("commands" to cmds.map { mapOf("code" to it.first, "value" to it.second) }))
        call(c, "POST", "/v1.0/devices/$id/commands", body = body)
    }

    /** Estado de un equipo. */
    suspend fun status(c: Context, id: String): Map<String, Any?> = withContext(Dispatchers.IO) {
        statusMap(call(c, "GET", "/v1.0/devices/$id/status").optJSONArray("result"))
    }

    // lista guardada para mostrar al instante al abrir Casa
    private fun saveCache(c: Context, list: List<TuyaDevice>) {
        val a = JSONArray()
        list.forEach { d ->
            val st = JSONObject()
            d.status.forEach { (k, v) -> st.put(k, v ?: JSONObject.NULL) }
            a.put(JSONObject().put("id", d.id).put("name", d.name).put("category", d.category).put("online", d.online)
                .put("product", d.product).put("status", st))
        }
        prefs(c).edit().putString("devices", a.toString()).apply()
    }

    fun cached(c: Context): List<TuyaDevice> = try {
        val a = JSONArray(prefs(c).getString("devices", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val so = o.optJSONObject("status") ?: JSONObject()
            val st = LinkedHashMap<String, Any?>()
            so.keys().forEach { k -> st[k] = so.opt(k).takeIf { it != JSONObject.NULL } }
            TuyaDevice(o.optString("id"), o.optString("name"), o.optString("category"), o.optBoolean("online"), o.optString("product"), st)
        }
    } catch (e: Exception) { emptyList() }

    // efectos propios del usuario
    fun loadEffects(c: Context): List<LightEffect> = try {
        val a = JSONArray(prefs(c).getString("effects", "[]") ?: "[]")
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val cs = o.getJSONArray("colors")
            LightEffect(
                o.optString("name"), o.optString("emoji", "✨"), o.optString("mode", "gradient"), o.optInt("speed", 50),
                (0 until cs.length()).map { j -> val x = cs.getJSONArray(j); Triple(x.getInt(0), x.getInt(1), x.getInt(2)) },
                custom = true
            )
        }
    } catch (e: Exception) { emptyList() }

    fun saveEffects(c: Context, list: List<LightEffect>) {
        val a = JSONArray()
        list.forEach { fx ->
            val cs = JSONArray()
            fx.colors.forEach { (h, s, v) -> cs.put(JSONArray().put(h).put(s).put(v)) }
            a.put(JSONObject().put("name", fx.name).put("emoji", fx.emoji).put("mode", fx.mode).put("speed", fx.speed).put("colors", cs))
        }
        prefs(c).edit().putString("effects", a.toString()).apply()
    }
}
