package com.loctracker.app

import com.google.android.gms.location.Priority
import com.loctracker.app.data.Recipient
import com.loctracker.app.delivery.DeliveryProcessor
import com.loctracker.app.domain.BatteryInfo
import com.loctracker.app.domain.BatteryPolicy
import com.loctracker.app.domain.Fix
import com.loctracker.app.domain.GpsStatus
import com.loctracker.app.domain.GpsStatusCalculator
import com.loctracker.app.domain.PhoneNumbers
import com.loctracker.app.domain.PowerTier
import com.loctracker.app.domain.Schedule
import com.loctracker.app.domain.WhatsAppLive
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DomainTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)
    private fun recipient(days: Int = 0b0011111, start: Int = 8 * 60, end: Int = 18 * 60) = Recipient(
        name = "A", phone = "491701234567", scheduleEnabled = true, scheduleDays = days,
        scheduleStartMinute = start, scheduleEndMinute = end,
    )

    // 2026-10-12 is a Monday.
    @Test fun activeInsideWindow() {
        assertNotNull(Schedule.active(recipient(), at(2026, 10, 12, 9, 0)))
        assertNull(Schedule.active(recipient(), at(2026, 10, 12, 19, 0)))
    }

    @Test fun weekendSkippedAndNextIsMonday() {
        val next = Schedule.next(recipient(), at(2026, 10, 10, 12, 0)) // Saturday
        assertEquals(at(2026, 10, 12, 8, 0), next!!.start)
    }

    @Test fun windowCrossingMidnightIsActiveNextMorning() {
        val r = recipient(days = 0b0000001, start = 22 * 60, end = 6 * 60) // Mondays 22:00-06:00
        val w = Schedule.active(r, at(2026, 10, 13, 2, 0)) // Tuesday 02:00
        assertEquals(at(2026, 10, 12, 22, 0), w!!.start)
        assertEquals(at(2026, 10, 13, 6, 0), w.end)
    }

    @Test fun disabledScheduleHasNoWindows() {
        assertNull(Schedule.next(recipient().copy(scheduleEnabled = false), at(2026, 10, 12, 9, 0)))
    }

    @Test fun phoneNormalisation() {
        assertEquals("491701234567", PhoneNumbers.normalize("+49 170 1234567"))
        assertEquals("491701234567", PhoneNumbers.normalize("0049-170-1234567"))
        assertEquals("491701234567", PhoneNumbers.normalize("491701234567"))
        assertNull(PhoneNumbers.normalize("0170 1234567")) // national format, no country code
        assertNull(PhoneNumbers.normalize("12345"))
        assertNull(PhoneNumbers.normalize("abc"))
    }

    @Test fun whatsAppLiveDurationRoundsUp() {
        assertEquals(15, WhatsAppLive.optionFor(10))
        assertEquals(60, WhatsAppLive.optionFor(30))
        assertEquals(60, WhatsAppLive.optionFor(60))
        assertEquals(480, WhatsAppLive.optionFor(120))
        assertEquals(480, WhatsAppLive.optionFor(1000))
    }

    @Test fun batteryPolicyTiers() {
        val full = BatteryPolicy.plan(60, BatteryInfo(80, false, false), stationary = false)
        assertEquals(PowerTier.FULL, full.tier); assertEquals(Priority.PRIORITY_HIGH_ACCURACY, full.priority)
        assertEquals(PowerTier.STATIONARY, BatteryPolicy.plan(60, BatteryInfo(80, false, false), true).tier)
        assertEquals(PowerTier.SAVER, BatteryPolicy.plan(60, BatteryInfo(14, false, false), false).tier)
        assertEquals(PowerTier.SAVER, BatteryPolicy.plan(60, BatteryInfo(80, false, true), false).tier)
        assertEquals(PowerTier.CRITICAL, BatteryPolicy.plan(60, BatteryInfo(4, false, false), false).tier)
        assertEquals(PowerTier.FULL, BatteryPolicy.plan(60, BatteryInfo(4, true, false), false).tier) // charging
        assertEquals(60_000L, full.intervalMs)
        assertEquals(300_000L, BatteryPolicy.plan(3600, BatteryInfo(80, true, false), false).intervalMs) // capped
    }

    @Test fun gpsStatus() {
        val now = 1_000_000L
        val good = Fix(0.0, 0.0, 10f, now - 5_000)
        assertEquals(GpsStatus.NO_PERMISSION, GpsStatusCalculator.compute(false, true, good, now))
        assertEquals(GpsStatus.GPS_OFF, GpsStatusCalculator.compute(true, false, good, now))
        assertEquals(GpsStatus.SEARCHING, GpsStatusCalculator.compute(true, true, null, now))
        assertEquals(GpsStatus.GOOD, GpsStatusCalculator.compute(true, true, good, now))
        assertEquals(GpsStatus.WEAK, GpsStatusCalculator.compute(true, true, good.copy(accuracyM = 120f), now))
        assertEquals(GpsStatus.STALE, GpsStatusCalculator.compute(true, true, good.copy(timeMs = now - 200_000), now))
    }

    @Test fun retryBackoffGrowsExponentially() {
        assertEquals(15_000L, DeliveryProcessor.backoffMs(1))
        assertEquals(30_000L, DeliveryProcessor.backoffMs(2))
        assertEquals(240_000L, DeliveryProcessor.backoffMs(5))
    }
}
