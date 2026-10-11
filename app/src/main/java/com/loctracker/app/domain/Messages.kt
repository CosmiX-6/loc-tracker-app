package com.loctracker.app.domain

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object Messages {
    fun mapsLink(lat: Double, lon: Double) = "https://maps.google.com/?q=" +
        String.format(Locale.US, "%.6f,%.6f", lat, lon)

    fun locationText(lat: Double, lon: Double, timeMs: Long): String {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMs))
        return "📍 My location at $time\n${mapsLink(lat, lon)}"
    }
}

/** WhatsApp's native Live Location only offers these durations. */
object WhatsAppLive {
    val OPTIONS_MINUTES = listOf(15, 60, 480)

    /** Smallest WhatsApp duration that covers the session, capped at 8 hours. */
    fun optionFor(sessionMinutes: Int): Int =
        OPTIONS_MINUTES.firstOrNull { it >= sessionMinutes } ?: OPTIONS_MINUTES.last()

    fun label(minutes: Int) = if (minutes < 60) "$minutes minutes" else "${minutes / 60} hour" + if (minutes >= 120) "s" else ""
}
