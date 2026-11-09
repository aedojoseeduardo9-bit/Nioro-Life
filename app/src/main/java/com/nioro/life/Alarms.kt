package com.nioro.life

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

object Scheduler {
    const val CHANNEL = "nioro_alarm"
    const val ACT_WAKE = "com.nioro.life.WAKE"
    const val ACT_BED = "com.nioro.life.BED"
    const val ACT_NOTE = "com.nioro.life.NOTE"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Alarmas y recordatorios", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Alarma de despertar, aviso para dormir y recordatorios de notas"
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        ch.setSound(sound, attrs)
        nm.createNotificationChannel(ch)
    }

    private fun noteCode(id: Long): Int = (id % 1_000_000L).toInt() + 1000

    private fun op(c: Context, action: String, code: Int, noteId: Long = 0L): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).putExtra("id", noteId)
        return PendingIntent.getBroadcast(
            c, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(c: Context): PendingIntent =
        PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAt(c: Context, at: Long, pi: PendingIntent) {
        try {
            val am = c.getSystemService(AlarmManager::class.java)
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent(c)), pi)
        } catch (e: SecurityException) {
            // Sin permiso de alarmas exactas: no se programa, pero la app no se cierra.
        }
    }

    fun cancelNote(c: Context, id: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(op(c, ACT_NOTE, noteCode(id), id))
    }

    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val s = Store.loadSettings(c)
        val notes = Store.loadNotes(c)
        val now = System.currentTimeMillis()

        am.cancel(op(c, ACT_WAKE, 1))
        am.cancel(op(c, ACT_BED, 2))
        for (n in notes) am.cancel(op(c, ACT_NOTE, noteCode(n.id), n.id))

        if (s.alarmOn) {
            val t = nextWake(s, now)
            if (t != null) setAt(c, t, op(c, ACT_WAKE, 1))
        }
        if (s.bedNotify) {
            val t = nextBedReminder(s, now)
            if (t != null) setAt(c, t, op(c, ACT_BED, 2))
        }
        for (n in notes) {
            val r = n.remindAt ?: continue
            if (!n.done && r > now) setAt(c, r, op(c, ACT_NOTE, noteCode(n.id), n.id))
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.ensureChannel(c)
        val s = Store.loadSettings(c)
        when (i.action) {
            Scheduler.ACT_WAKE ->
                post(c, 1, "Nioro Life", "Buenos días ☀️ Es hora de despertar", true)

            Scheduler.ACT_BED ->
                post(c, 2, "Nioro Life", "Te duermes a las ${fmtTime(s.bedMin)} (en ${s.leadMin} min)", false)

            Scheduler.ACT_NOTE -> {
                val id = i.getLongExtra("id", 0L)
                val notes = Store.loadNotes(c)
                val n = notes.firstOrNull { it.id == id }
                if (n != null) {
                    Store.saveNotes(c, notes.map { if (it.id == id) it.copy(done = true) else it })
                    val title = if (n.title.isBlank()) "Recordatorio" else n.title
                    post(c, 3000 + (id % 100_000L).toInt(), title, n.text.take(120), false)
                }
            }
        }
        Scheduler.scheduleAll(c)
    }

    private fun post(c: Context, id: Int, title: String, text: String, loud: Boolean) {
        val open = PendingIntent.getActivity(
            c, 0,
            Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(c, Scheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_moon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(open)
        if (loud) b.setFullScreenIntent(open, true)
        val n = b.build()
        if (loud) n.flags = n.flags or Notification.FLAG_INSISTENT
        c.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.ensureChannel(c)
        Scheduler.scheduleAll(c)
    }
}
