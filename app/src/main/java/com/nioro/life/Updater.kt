package com.nioro.life

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Busca y descarga nuevas versiones desde las Releases del repositorio de GitHub (Wi-Fi o datos móviles). */
object Updater {
    const val REPO = "aedojoseeduardo9-bit/Nioro-Life"

    data class Remote(val build: Long, val url: String, val size: Long)

    fun isWifi(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Hay conexión a internet (Wi-Fi, datos móviles u otra). */
    fun isOnline(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun installedBuild(c: Context): Long =
        PackageInfoCompat.getLongVersionCode(c.packageManager.getPackageInfo(c.packageName, 0))

    fun installedName(c: Context): String =
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: ""

    fun fetchRemote(): Remote {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "NioroLife")
        try {
            if (conn.responseCode != 200) throw IOException("GitHub respondió ${conn.responseCode}")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(body)
            val notes = o.optString("body", "")
            val build = Regex("build:(\\d+)").find(notes)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val assets = o.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Remote(build, a.getString("browser_download_url"), a.optLong("size", 0L))
                }
            }
            throw IOException("La versión publicada no tiene APK")
        } finally {
            conn.disconnect()
        }
    }

    fun download(c: Context, r: Remote, onProgress: (Int) -> Unit): File {
        val dir = File(c.cacheDir, "updates")
        dir.mkdirs()
        val out = File(dir, "NioroLife.apk")
        if (out.exists()) out.delete()
        val conn = URL(r.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "NioroLife")
        try {
            if (conn.responseCode != 200) throw IOException("Descarga rechazada (${conn.responseCode})")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else r.size
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val read = input.read(buf)
                        if (read < 0) break
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != last) {
                                last = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (out.length() < 500_000L) throw IOException("Descarga incompleta")
        return out
    }

    fun installIntent(c: Context, f: File): Intent {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
