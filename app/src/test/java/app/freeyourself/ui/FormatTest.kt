package app.freeyourself.ui

import app.freeyourself.core.DayStats
import app.freeyourself.core.Policy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val nb = ' '   // número e unidade não se separam na quebra de linha

    @Test fun durations() {
        assertEquals("30${nb}s", formatDuration(30_000))
        assertEquals("8${nb}min", formatDuration(480_000))
        assertEquals("1${nb}h 4${nb}min", formatDuration(3_840_000))
        assertEquals("2${nb}h", formatDuration(7_200_000))
        assertEquals("0${nb}s", formatDuration(0))
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
        assertEquals("Uma nova detecção hoje bloqueia o app por 30${nb}s.", nextStep(DayStats(d, warnings = 3), p))
        assertEquals("Uma nova detecção hoje bloqueia o app por 1${nb}min.", nextStep(DayStats(d, warnings = 3, blocks = 1), p))
    }

    @Test fun weekdayInPortuguese() {
        assertEquals("Seg", weekday(LocalDate.parse("2026-10-05")))
        assertEquals("Dom", weekday(LocalDate.parse("2026-10-04")))
    }

    @Test fun resetCountdownFollowsTheCountersDay() {
        val sp = ZoneId.of("America/Sao_Paulo")
        val now = LocalDateTime.parse("2026-10-04T23:30").atZone(sp)
        assertEquals(30 * 60_000L, msUntilReset(now, LocalDate.parse("2026-10-04")))
        // Viajou para oeste depois da meia-noite: o relógio diz dia 4, mas o contador já é do dia 5.
        val west = LocalDateTime.parse("2026-10-04T23:30").atZone(ZoneId.of("America/Manaus"))
        assertEquals(24 * 3_600_000L + 30 * 60_000L, msUntilReset(west, LocalDate.parse("2026-10-05")))
    }

    @Test fun blockFootnoteAfterMidnight() {
        assertEquals("Hoje: 5ª detecção. À meia-noite tudo recomeça.", blockFootnote(5))
        assertEquals("Este bloqueio começou ontem. Os contadores de hoje já recomeçaram.", blockFootnote(0))
    }
}
