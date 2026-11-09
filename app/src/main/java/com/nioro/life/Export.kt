package com.nioro.life

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Exporta las finanzas a un archivo CSV ordenado por mes, que se envía a Drive / OneDrive con «Compartir». */
object Export {
    const val THIS_MONTH = 0
    const val LAST_MONTH = 1
    const val ALL_MONTHS = 2

    private val TYPES = listOf("Ingreso", "Gasto", "Deuda")
    private val LOCALE = Locale("es", "CL")

    private fun monthKey(ms: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun monthLabel(ms: Long): String =
        SimpleDateFormat("MMMM yyyy", LOCALE).format(Date(ms)).replaceFirstChar { it.uppercase() }

    /** Evita que una hoja de cálculo interprete un texto del usuario como fórmula. */
    private fun cell(raw: String): String {
        var s = raw.replace("\r", " ").replace("\n", " ").trim()
        if (s.isNotEmpty() && s[0] in "=+-@") s = "'$s"
        return if (s.contains(';') || s.contains('"')) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    private fun row(vararg cols: String): String = cols.joinToString(";") + "\r\n"

    /** Devuelve el texto del CSV o null si no hay movimientos en el período pedido. */
    fun buildCsv(tx: List<Tx>, scope: Int, now: Long): String? {
        val nowKey = monthKey(now)
        val list = when (scope) {
            THIS_MONTH -> tx.filter { monthKey(it.date) == nowKey }
            LAST_MONTH -> tx.filter { monthKey(it.date) == nowKey - 1 }
            else -> tx
        }
        if (list.isEmpty()) return null

        val dateFmt = SimpleDateFormat("dd-MM-yyyy", LOCALE)
        val sb = StringBuilder()
        sb.append('﻿') // para que Excel lea bien las tildes
        sb.append(row("Nioro Life - Finanzas"))
        sb.append(row("Generado", dateFmt.format(Date(now))))
        sb.append(row("Montos en pesos, sin separador de miles"))
        sb.append(row())
        sb.append(row("Mes", "Fecha", "Tipo", "Descripción", "Motivo", "Monto", "Estado"))

        val byMonth = list.groupBy { monthKey(it.date) }.toSortedMap()
        for ((_, items) in byMonth) {
            val sorted = items.sortedBy { it.date }
            val label = monthLabel(sorted.first().date)
            for (t in sorted) {
                val estado = if (t.kind == 2) (if (t.paid) "Pagada" else "Pendiente") else ""
                sb.append(
                    row(
                        cell(label), dateFmt.format(Date(t.date)), TYPES.getOrElse(t.kind) { "Gasto" },
                        cell(t.title), cell(t.reason), t.amount.toString(), estado
                    )
                )
            }
            val s = monthSummary(sorted, sorted.first().date)
            sb.append(row(cell(label), "", "Resumen: ingresos", "", "", s.income.toString(), ""))
            sb.append(row(cell(label), "", "Resumen: gastos (con deudas pagadas)", "", "", s.expenses.toString(), ""))
            sb.append(row(cell(label), "", "Resumen: deudas pendientes", "", "", s.pending.toString(), ""))
            sb.append(row(cell(label), "", "Resumen: balance del mes", "", "", s.balance.toString(), ""))
            sb.append(row())
        }
        return sb.toString()
    }

    /** Escribe el archivo y devuelve el intent de «Compartir» (Drive, OneDrive, correo…). Null si no hay datos. */
    fun shareIntent(c: Context, tx: List<Tx>, scope: Int): Intent? {
        val now = System.currentTimeMillis()
        val csv = buildCsv(tx, scope, now) ?: return null
        val dir = File(c.cacheDir, "exports")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val ref = if (scope == LAST_MONTH) {
            Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }.timeInMillis
        } else now
        val stamp = SimpleDateFormat("yyyy-MM", LOCALE).format(Date(ref))
        val name = if (scope == ALL_MONTHS) "NioroLife_Finanzas_todos_hasta_$stamp.csv" else "NioroLife_Finanzas_$stamp.csv"
        val f = File(dir, name)
        f.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Nioro Life - Finanzas")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        return Intent.createChooser(send, "Guardar en Drive, OneDrive…")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
