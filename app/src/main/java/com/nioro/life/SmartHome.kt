package com.nioro.life

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hogar: equipos Wi-Fi con firmware libre (OpenBeken o Tasmota), controlados por la red local.
 * Los dos firmwares entienden los comandos HTTP de Tasmota: http://IP/cm?cmnd=Power%20Toggle
 */

/** Un equipo guardado. `kind`: 0 = ampolleta de color (por ahora el único tipo). */
data class SmartDevice(val id: Long, val name: String, val ip: String, val kind: Int = 0, val room: String = "")

/** Último estado conocido de una ampolleta. */
data class BulbState(
    val online: Boolean = false,
    val on: Boolean = false,
    /** 0–100 */
    val dimmer: Int = 100,
    /** 0–360 */
    val hue: Int = 40,
    /** 0–100 */
    val sat: Int = 0,
    /** Temperatura de blanco en mired (153 = frío 6500 K, 500 = cálido 2000 K). */
    val ct: Int = 300,
    /** true = modo blanco, false = modo color */
    val white: Boolean = true,
    /** true = está corriendo un efecto/escena animada */
    val scene: Boolean = false
)

/** Una escena: modo blanco (ct) o color (hue/sat), con brillo. */
data class LightScene(val name: String, val emoji: String, val white: Boolean, val hue: Int, val sat: Int, val ct: Int, val dimmer: Int)

val LIGHT_SCENES = listOf(
    LightScene("Lectura", "📖", true, 0, 0, 220, 100),
    LightScene("Relax", "🛋️", true, 0, 0, 420, 45),
    LightScene("Noche", "🌙", false, 18, 90, 0, 8),
    LightScene("Atardecer", "🌅", false, 28, 85, 0, 70),
    LightScene("Océano", "🌊", false, 200, 80, 0, 70),
    LightScene("Bosque", "🌿", false, 125, 70, 0, 60),
    LightScene("Romance", "💗", false, 330, 75, 0, 50),
    LightScene("Fiesta", "🎉", false, 280, 100, 0, 100)
)

// ---------------------------------------------------------------- cálculos puros (con pruebas)

fun miredToKelvin(mired: Int): Int = if (mired <= 0) 0 else (1_000_000.0 / mired).roundToInt()
fun kelvinToMired(k: Int): Int = if (k <= 0) 0 else (1_000_000.0 / k).roundToInt()

/** "120,100,50" → (120, 100, 50). Devuelve null si no tiene ese formato. */
fun parseHsb(s: String): Triple<Int, Int, Int>? {
    val p = s.split(',').map { it.trim().toDoubleOrNull() ?: return null }
    if (p.size < 3) return null
    return Triple(p[0].roundToInt(), p[1].roundToInt(), p[2].roundToInt())
}

/** Color HSV a ARGB (Int), para pintar la ampolleta en pantalla. */
fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val c = v * s
    val x = c * (1 - abs(hh % 2 - 1))
    val (r, g, b) = when (hh.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    fun ch(f: Float) = ((f + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

/** Color aproximado de un blanco según su temperatura (mired), para dibujarlo. */
fun whiteArgb(mired: Int): Int {
    val t = ((mired - 153f) / (500f - 153f)).coerceIn(0f, 1f) // 0 frío, 1 cálido
    val r = 255
    val g = (235 - 60 * t).roundToInt()
    val b = (255 - 160 * t).roundToInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** Color con el que se dibuja la ampolleta (sin contar el brillo). */
fun bulbArgb(s: BulbState): Int = if (s.white) whiteArgb(s.ct) else hsvToArgb(s.hue.toFloat(), s.sat / 100f, 1f)

/** URL de un comando Tasmota/OpenBeken. */
fun cmdUrl(ip: String, cmd: String): String =
    "http://$ip/cm?cmnd=" + URLEncoder.encode(cmd, "UTF-8").replace("+", "%20")

/** Todas las IP de la subred /24 de `ip` (sin la propia). */
fun subnetHosts(ip: String): List<String> {
    val parts = ip.split('.')
    if (parts.size != 4) return emptyList()
    val base = parts.take(3).joinToString(".")
    return (1..254).map { "$base.$it" }.filter { it != ip }
}

/** IP válida tipo 192.168.1.20 */
fun isIpv4(s: String): Boolean {
    val p = s.trim().split('.')
    return p.size == 4 && p.all { x -> x.toIntOrNull()?.let { it in 0..255 } == true && x.isNotEmpty() }
}

// ---------------------------------------------------------------- red

object SmartHome {
    private fun prefs(c: Context) = c.getSharedPreferences("nioro_home", Context.MODE_PRIVATE)

    fun load(c: Context): List<SmartDevice> = try {
        val a = JSONArray(prefs(c).getString("devices", "[]") ?: "[]")
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            SmartDevice(o.optLong("id"), o.optString("name"), o.optString("ip"), o.optInt("kind"), o.optString("room"))
        }
    } catch (e: Exception) { emptyList() }

    fun save(c: Context, list: List<SmartDevice>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("ip", it.ip).put("kind", it.kind).put("room", it.room)) }
        prefs(c).edit().putString("devices", a.toString()).apply()
    }

    /** Hace una petición GET y devuelve el texto, o null si no responde. */
    private fun get(url: String, timeout: Int, network: Network? = null): String? = try {
        val u = URL(url)
        val conn = (network?.openConnection(u) ?: u.openConnection()) as HttpURLConnection
        conn.connectTimeout = timeout
        conn.readTimeout = timeout + 1500
        conn.setRequestProperty("User-Agent", "NioroLife")
        try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    /** Lee el estado a partir de la respuesta JSON de Tasmota/OpenBeken (acepta varias formas). */
    fun parseState(json: String?, prev: BulbState = BulbState()): BulbState {
        if (json == null) return prev.copy(online = false)
        return try {
            var o = JSONObject(json)
            o.optJSONObject("StatusSTS")?.let { o = it }
            val power = (o.optString("POWER", "").ifEmpty { o.optString("POWER1", "") }).uppercase()
            val hsb = parseHsb(o.optString("HSBColor", ""))
            val dimmer = if (o.has("Dimmer")) o.optInt("Dimmer", prev.dimmer) else hsb?.third ?: prev.dimmer
            val ct = if (o.has("CT")) o.optInt("CT", prev.ct) else prev.ct
            val whiteCh = o.optInt("White", -1)
            // modo blanco si el canal blanco está activo o el color no tiene saturación
            val white = when {
                whiteCh > 0 -> true
                hsb != null -> hsb.second == 0
                else -> prev.white
            }
            BulbState(
                online = true,
                on = if (power.isEmpty()) prev.on else power == "ON" || power == "1",
                dimmer = dimmer.coerceIn(0, 100),
                hue = hsb?.first ?: prev.hue,
                sat = hsb?.second ?: prev.sat,
                ct = ct.coerceIn(153, 500),
                white = white
            )
        } catch (e: Exception) {
            prev.copy(online = true)
        }
    }

    /** Envía un comando y devuelve el estado que responde el equipo. */
    suspend fun send(ip: String, cmd: String, prev: BulbState): BulbState = withContext(Dispatchers.IO) {
        parseState(get(cmdUrl(ip, cmd), 2500), prev)
    }

    suspend fun refresh(ip: String, prev: BulbState): BulbState = withContext(Dispatchers.IO) {
        // "State" da POWER, Dimmer, HSBColor y CT; si no lo entiende, se intenta con "Status 11"
        val r = get(cmdUrl(ip, "State"), 2000) ?: get(cmdUrl(ip, "Status 11"), 2000)
        parseState(r, prev)
    }

    /** Nombre que el propio equipo dice tener (FriendlyName / DeviceName), si lo informa. */
    private fun deviceName(ip: String): String? {
        val r = get(cmdUrl(ip, "Status"), 1500) ?: return null
        return try {
            val st = JSONObject(r).optJSONObject("Status") ?: return null
            st.optJSONArray("FriendlyName")?.optString(0)?.takeIf { it.isNotBlank() }
                ?: st.optString("DeviceName").takeIf { it.isNotBlank() }
        } catch (e: Exception) { null }
    }

    /** IP del teléfono en el Wi-Fi de la casa, o null si no está en Wi-Fi. */
    fun phoneIp(c: Context): String? {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return null
        val net = wifiNetwork(c) ?: cm.activeNetwork ?: return null
        val lp = cm.getLinkProperties(net) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    fun wifiNetwork(c: Context): Network? {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return null
        @Suppress("DEPRECATION")
        return cm.allNetworks.firstOrNull { n ->
            cm.getNetworkCapabilities(n)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    /** Equipo encontrado al buscar en la red. */
    data class Found(val ip: String, val name: String)

    /** Recorre la red de la casa buscando equipos que respondan a los comandos de Tasmota/OpenBeken. */
    suspend fun scan(c: Context, onProgress: (Int) -> Unit): List<Found> = withContext(Dispatchers.IO) {
        val ip = phoneIp(c) ?: return@withContext emptyList()
        val hosts = subnetHosts(ip)
        val gate = Semaphore(40)
        var done = 0
        coroutineScope {
            hosts.map { h ->
                async {
                    gate.withPermit {
                        val r = get(cmdUrl(h, "Power"), 700)
                        synchronized(this@SmartHome) { done++; onProgress(done * 100 / hosts.size) }
                        if (r != null && r.contains("POWER", ignoreCase = true)) Found(h, deviceName(h) ?: "Ampolleta ${h.substringAfterLast('.')}") else null
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /**
     * Configura el Wi-Fi de una ampolleta recién liberada. El teléfono debe estar conectado a la red
     * que crea la ampolleta (OpenBeken_xxxx o tasmota-xxxx), que la deja en 192.168.4.1.
     * Prueba el formato de OpenBeken y el de Tasmota. Devuelve true si alguno respondió.
     */
    suspend fun sendWifi(c: Context, ssid: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val net = wifiNetwork(c)
        val s = URLEncoder.encode(ssid, "UTF-8")
        val p = URLEncoder.encode(pass, "UTF-8")
        val obk = get("http://192.168.4.1/cfg_wifi_set?ssid=$s&pass=$p", 4000, net)
        val tas = if (obk == null) get("http://192.168.4.1/wi?s1=$s&p1=$p&save=", 4000, net) else null
        obk != null || tas != null
    }
}
