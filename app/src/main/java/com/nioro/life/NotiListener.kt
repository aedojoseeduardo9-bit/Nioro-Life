package com.nioro.life

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Un mensaje que llegó como notificación (WhatsApp, Gmail, Mensajes, Telegram…). */
data class IncomingMsg(
    val id: String,
    val app: String,
    val pkg: String,
    val from: String,
    val text: String,
    val time: Long,
    val canReply: Boolean
)

/**
 * Escucha las notificaciones (con permiso del usuario) para que el asistente pueda leer los mensajes
 * nuevos y responderlos con la acción "Responder" de la propia notificación. Todo queda en el teléfono.
 */
class NotiListener : NotificationListenerService() {

    override fun onListenerConnected() {
        try { activeNotifications?.forEach { capture(it) } } catch (e: Exception) { }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = capture(sbn)

    private fun capture(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = messageText(ex)
            if (title.isBlank() && text.isBlank()) return
            val reply = replyAction(n)
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) { sbn.packageName }
            val id = (sbn.key.hashCode().toLong() and 0xFFFFFF).toString(36)
            val msg = IncomingMsg(id, label, sbn.packageName, title, text.take(600), sbn.postTime, reply != null)
            synchronized(lock) {
                recent.removeAll { it.id == id }
                recent.add(0, msg)
                if (reply != null) replies[id] = reply
                while (recent.size > 60) recent.removeAt(recent.size - 1).also { replies.remove(it.id) }
            }
        } catch (e: Exception) { }
    }

    companion object {
        private val lock = Any()
        private val recent = mutableListOf<IncomingMsg>()
        private val replies = HashMap<String, Notification.Action>()

        /** ¿El usuario dio "Acceso a notificaciones" a Nioro Life? */
        fun enabled(c: Context): Boolean {
            val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(c, NotiListener::class.java).flattenToString()
            return flat.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun messages(appFilter: String? = null, max: Int = 10): List<IncomingMsg> = synchronized(lock) {
            val f = appFilter?.let { normalizeName(it) }?.takeIf { it.isNotBlank() }
            recent.filter { f == null || normalizeName(it.app).contains(f) || it.pkg.contains(f) }.take(max)
        }

        /** Responde usando la acción "Responder" de la notificación. */
        fun reply(c: Context, id: String, text: String): Boolean {
            val action = synchronized(lock) { replies[id] } ?: return false
            val inputs = action.remoteInputs ?: return false
            val results = Bundle()
            inputs.forEach { results.putCharSequence(it.resultKey, text) }
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(inputs, intent, results)
            return try {
                action.actionIntent.send(c, 0, intent)
                true
            } catch (e: PendingIntent.CanceledException) {
                false
            }
        }

        private fun messageText(ex: Bundle): String {
            // conversaciones (WhatsApp, Mensajes): el último mensaje de cada remitente
            val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (msgs != null && msgs.isNotEmpty()) {
                val lines = msgs.mapNotNull { (it as? Bundle)?.getCharSequence("text")?.toString() }
                if (lines.isNotEmpty()) return lines.takeLast(4).joinToString("\n")
            }
            ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { if (it.isNotBlank()) return it.toString() }
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.let { if (it.isNotEmpty()) return it.joinToString("\n") }
            return ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        }

        /** La acción "Responder": primero la marcada como tal (Android 9+), si no, la primera que acepta texto. */
        private fun replyAction(n: Notification): Notification.Action? {
            val acts = n.actions ?: return null
            fun takesText(a: Notification.Action) = a.remoteInputs?.any { it.allowFreeFormInput } == true
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                acts.firstOrNull { takesText(it) && it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }?.let { return it }
            }
            return acts.firstOrNull { takesText(it) }
        }
    }
}
