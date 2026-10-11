package com.loctracker.app.domain

import com.loctracker.app.data.Recipient
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class Window(val start: ZonedDateTime, val end: ZonedDateTime)

/** Weekly recurring sharing windows. Windows may cross midnight (e.g. 22:00-06:00). */
object Schedule {
    /** Monday = bit 0 ... Sunday = bit 6. */
    fun dayBit(day: java.time.DayOfWeek): Int = 1 shl (day.value - 1)

    fun windows(r: Recipient, around: ZonedDateTime, zone: ZoneId = around.zone): List<Window> {
        if (!r.scheduleEnabled || r.scheduleDays == 0) return emptyList()
        val length = ((r.scheduleEndMinute - r.scheduleStartMinute + 1440) % 1440).let { if (it == 0) 1440 else it }
        val first = around.toLocalDate().minusDays(1)
        return (0..8).mapNotNull { offset ->
            val date = first.plusDays(offset.toLong())
            if (r.scheduleDays and dayBit(date.dayOfWeek) == 0) return@mapNotNull null
            val start = ZonedDateTime.of(
                date,
                LocalTime.of(r.scheduleStartMinute / 60, r.scheduleStartMinute % 60),
                zone,
            )
            Window(start, start.plusMinutes(length.toLong()))
        }
    }

    fun active(r: Recipient, now: ZonedDateTime): Window? =
        windows(r, now).firstOrNull { !now.isBefore(it.start) && now.isBefore(it.end) }

    fun next(r: Recipient, now: ZonedDateTime): Window? =
        windows(r, now).filter { it.start.isAfter(now) }.minByOrNull { it.start }
}
