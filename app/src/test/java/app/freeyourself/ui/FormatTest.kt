package app.freeyourself.ui

import app.freeyourself.core.DayStats
import app.freeyourself.core.Policy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun durations() {
        assertEquals("30 s", formatDuration(30_000))
        assertEquals("8 min", formatDuration(480_000))
        assertEquals("1 h 4 min", formatDuration(3_840_000))
        assertEquals("2 h", formatDuration(7_200_000))
        assertEquals("0 s", formatDuration(0))
    }

    @Test fun countdown() {
        assertEquals("01:42", formatClock(102_000))
        assertEquals("00:01", formatClock(500))
        assertEquals("1:02:05", formatClock(3_725_000))
    }

    @Test fun nextStepText() {
        val p = Policy()
        val d = LocalDate.parse("2026-10-04")
        assertEquals("3 avisos antes do primeiro bloqueio.", nextStep(DayStats(d), p))
        assertEquals("1 aviso antes do primeiro bloqueio.", nextStep(DayStats(d, warnings = 2), p))
        assertEquals("Uma nova detecção hoje bloqueia o app por 30 s.", nextStep(DayStats(d, warnings = 3), p))
        assertEquals("Uma nova detecção hoje bloqueia o app por 1 min.", nextStep(DayStats(d, warnings = 3, blocks = 1), p))
    }

    @Test fun weekdayInPortuguese() {
        assertEquals("Seg", weekday(LocalDate.parse("2026-10-05")))
        assertEquals("Dom", weekday(LocalDate.parse("2026-10-04")))
    }
}
