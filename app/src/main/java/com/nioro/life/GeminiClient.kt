package com.nioro.life

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cerebro del asistente: la API de Gemini (Google AI Studio) por HTTPS.
 * La clave la crea el usuario gratis en aistudio.google.com y se guarda solo en el teléfono.
 */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    /** Alias que Google mantiene apuntando al modelo Flash vigente. */
    const val DEFAULT_MODEL = "gemini-flash-latest"

    class GeminiException(msg: String) : IOException(msg)

    private fun prefs(c: Context) = c.getSharedPreferences("nioro_ai", Context.MODE_PRIVATE)

    fun key(c: Context): String? = prefs(c).getString("key", null)?.takeIf { it.isNotBlank() }
    fun model(c: Context): String = prefs(c).getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun speakAlways(c: Context): Boolean = prefs(c).getBoolean("speak", false)

    fun saveKey(c: Context, key: String?) {
        val e = prefs(c).edit()
        if (key == null) e.remove("key") else e.putString("key", key.trim())
        e.apply()
    }
    fun saveModel(c: Context, m: String) = prefs(c).edit().putString("model", m).apply()
    fun saveSpeakAlways(c: Context, v: Boolean) = prefs(c).edit().putBoolean("speak", v).apply()

    private fun http(key: String, method: String, path: String, body: String?): JSONObject {
        val conn = URL("$BASE/$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 90_000
        conn.requestMethod = method
        conn.setRequestProperty("x-goog-api-key", key)
        conn.setRequestProperty("Content-Type", "application/json")
        try {
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code !in 200..299) {
                val err = json.optJSONObject("error")
                throw GeminiException(geminiError(code, err?.optString("status") ?: "", err?.optString("message") ?: text.take(200)))
            }
            return json
        } finally {
            conn.disconnect()
        }
    }

    /** Modelos de texto disponibles para esta clave (sin los de imagen, voz o embeddings). */
    suspend fun listModels(key: String): List<String> = withContext(Dispatchers.IO) {
        val r = http(key, "GET", "models?pageSize=200", null)
        val arr = r.optJSONArray("models") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
            val id = m.optString("name").removePrefix("models/")
            id.takeIf { methods.contains("generateContent") && usableModel(it) }
        }.sortedWith(compareByDescending<String> { modelRank(it) }.thenByDescending { it })
    }

    /** Una vuelta de conversación. `contents` es el historial en el formato de Gemini. */
    suspend fun generate(c: Context, system: String, contents: JSONArray, tools: JSONArray?): JSONObject = withContext(Dispatchers.IO) {
        val key = key(c) ?: throw GeminiException("Falta la clave de Gemini: configúrala en el asistente.")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        if (tools != null) body.put("tools", tools)
        http(key, "POST", "models/${model(c)}:generateContent", body.toString())
    }

    /** Pregunta suelta con búsqueda de Google (para noticias, clima, horarios, datos al día). */
    suspend fun searchWeb(c: Context, question: String): String {
        val contents = JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))
        )
        val tools = JSONArray().put(JSONObject().put("google_search", JSONObject()))
        val r = generate(
            c, "Responde en español de Chile, breve y con datos concretos y actuales. Di la fecha de la información si importa.",
            contents, tools
        )
        return responseText(r).ifBlank { "No encontré información." }
    }
}

/** Texto de la respuesta (sin las partes de "pensamiento"). */
fun responseText(r: JSONObject): String {
    val parts = r.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: return ""
    return (0 until parts.length()).mapNotNull { i ->
        val p = parts.getJSONObject(i)
        if (p.optBoolean("thought")) null else p.optString("text").takeIf { it.isNotBlank() }
    }.joinToString("\n").trim()
}

/** Modelos de chat útiles (fuera los de imagen, audio, embeddings, etc.). */
fun usableModel(id: String): Boolean {
    val x = id.lowercase()
    if (!x.startsWith("gemini")) return false
    return listOf("embedding", "image", "tts", "audio", "live", "vision", "aqa", "robotics", "computer-use").none { x.contains(it) }
}

/** Orden sugerido: primero los alias "latest", luego Flash, luego Pro, luego Lite. */
fun modelRank(id: String): Int {
    val x = id.lowercase()
    var r = 0
    if (x.contains("latest")) r += 100
    if (x.contains("flash")) r += 50
    if (x.contains("pro")) r += 30
    if (x.contains("lite")) r -= 25
    if (x.contains("preview") || x.contains("exp")) r -= 5
    return r
}

/** Mensaje claro para los errores de Gemini. */
fun geminiError(code: Int, status: String, msg: String): String = when {
    status == "INVALID_ARGUMENT" && msg.contains("API key", ignoreCase = true) -> "La clave de Gemini no es válida. Revísala en aistudio.google.com."
    code == 400 && msg.contains("API_KEY", ignoreCase = true) -> "La clave de Gemini no es válida. Revísala en aistudio.google.com."
    code == 403 || status == "PERMISSION_DENIED" -> "Esa clave no tiene permiso para usar Gemini. Crea otra en aistudio.google.com."
    code == 404 || status == "NOT_FOUND" -> "Ese modelo ya no existe. Elige otro en los ajustes del asistente."
    code == 429 || status == "RESOURCE_EXHAUSTED" -> "Llegaste al límite gratis de Gemini por ahora. Espera un minuto (o hasta mañana) o elige otro modelo."
    code >= 500 -> "Gemini está con problemas en este momento. Intenta de nuevo en un rato."
    else -> "Gemini respondió: $msg ($code)"
}
