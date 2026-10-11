package com.loctracker.app.ui

import com.loctracker.app.data.Recipient
import java.text.DateFormat
import java.util.Date

val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun clock(ms: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
fun dateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun minutesOfDay(m: Int) = "%02d:%02d".format(m / 60, m % 60)

fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun remaining(ms: Long): String {
    val m = ((ms + 59_999) / 60_000).coerceAtLeast(0)
    return if (m < 60) "$m min left" else "${m / 60} h %02d min left".format(m % 60)
}

fun scheduleSummary(r: Recipient): String {
    if (!r.scheduleEnabled) return "Manual start"
    val days = if (r.scheduleDays == 0b1111111) "Every day" else
        DAY_LABELS.filterIndexed { i, _ -> r.scheduleDays and (1 shl i) != 0 }.joinToString(" ")
    return "$days ${minutesOfDay(r.scheduleStartMinute)}–${minutesOfDay(r.scheduleEndMinute)}"
}
