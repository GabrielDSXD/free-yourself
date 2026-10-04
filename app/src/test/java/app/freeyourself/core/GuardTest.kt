package app.freeyourself.core

import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardTest {
    private var clock = FakeClock()
    private var saved = emptyMap<String, String>()
    private fun guard(policy: () -> Policy = { Policy() }) = Guard(clock, policy, saved) { saved = it }
    private fun Guard.reachBlock(pkg: String = "x") = repeat(4) { onDetection(pkg) }

    @Test fun countsAttempts() {
        val g = guard()
        assertEquals(Decision.Warn(1), g.onDetection("x")); assertEquals(1, g.today.attempts)
        assertEquals(Decision.Warn(2), g.onDetection("x")); assertEquals(2, g.today.attempts)
        assertEquals(Decision.Warn(3), g.onDetection("x")); assertEquals(3, g.today.attempts)
    }

    @Test fun blocksGrowAfterWarnings() {
        val g = guard()
        repeat(3) { g.onDetection("x") }
        val durations = (4..6).map {
            assertEquals(Decision.Block, g.onDetection("x"))
            g.remainingMs("x").also { clock.advance(it) }
        }
        assertEquals(listOf(30_000L, 60_000L, 120_000L), durations)
        assertEquals(3, g.today.blocks)
        assertEquals(210_000L, g.today.blockedMs)
    }

    @Test fun activeBlockReshowsWithoutCounting() {
        val g = guard(); g.reachBlock()
        clock.advance(5_000)
        assertEquals(Decision.Block, g.onDetection("x"))
        assertEquals(4, g.today.attempts)
        assertEquals(25_000L, g.remainingMs("x"))
    }

    @Test fun graceAfterContinue() {
        val g = guard()
        g.onDetection("x"); g.onContinue()
        clock.advance(29_000)
        assertEquals(Decision.Ignore, g.onDetection("x"))
        clock.advance(2_000)
        assertEquals(Decision.Warn(2), g.onDetection("x"))
    }

    @Test fun settleAfterLeave() {
        val g = guard()
        g.onDetection("x"); g.onLeave()
        clock.advance(2_000)
        assertEquals(Decision.Ignore, g.onDetection("x"))
        clock.advance(2_000)
        assertEquals(Decision.Warn(2), g.onDetection("x"))
    }

    @Test fun resetsAtMidnight() {
        clock = FakeClock("2026-10-04T23:59")
        val g = guard()
        (1..8).forEach { g.onDetection("app$it") }
        assertEquals(8, g.today.attempts)
        clock.advance(2.minutes.inWholeMilliseconds)          // 2026-10-05 00:01
        g.tick()
        assertEquals(LocalDate.parse("2026-10-05"), g.today.date)
        assertEquals(0, g.today.attempts)
        assertEquals(8, g.history.first().attempts)
        assertEquals(Decision.Warn(1), g.onDetection("y"))
    }

    @Test fun blockCrossingMidnightRunsToEnd() {
        clock = FakeClock("2026-10-04T23:59:50")
        val g = guard(); g.reachBlock()                       // 30 s
        clock.advance(20_000)                                 // 00:00:10
        g.tick()
        assertEquals(0, g.today.attempts)
        assertEquals(10_000L, g.remainingMs("x"))
    }

    @Test fun survivesProcessRestart() {
        guard().reachBlock()
        clock.advance(10_000)
        val restarted = guard()
        assertEquals(20_000L, restarted.remainingMs("x"))
        assertEquals(4, restarted.today.attempts)
    }

    @Test fun survivesReboot() {
        guard().reachBlock()
        clock.advance(10_000)
        clock.reboot(offMs = 10_000)
        assertEquals(10_000L, guard().remainingMs("x"))
    }

    @Test fun rebootWithoutBootCountUsesWallClock() {
        clock.boot = 0                                        // aparelho que não expõe BOOT_COUNT
        guard().reachBlock()                                  // 30 s, termina em elapsed 3 630 000
        clock.wall += 10_000
        clock.elapsed = 9_000_000                             // reiniciou e já tem mais uptime que antes
        assertEquals(20_000L, guard().remainingMs("x"))
    }

    @Test fun rebootNeverExtendsBlock() {
        guard().reachBlock()
        clock.wall -= 3_600_000                               // relógio atrasado 1 h
        clock.reboot(offMs = 0)
        assertTrue(guard().remainingMs("x") <= 30_000L)
    }

    @Test fun clockForwardDoesNotResetDay() {
        val g = guard()
        g.onDetection("x"); g.onDetection("x")
        clock.wall += 2 * 86_400_000L                         // usuário adianta 2 dias
        g.tick()
        assertEquals(2, g.today.attempts)
        assertEquals(2, guard().today.attempts)               // nem após reiniciar o processo
    }

    @Test fun clockForwardDoesNotShortenBlock() {
        val g = guard(); g.reachBlock()
        clock.wall += 3_600_000
        assertEquals(30_000L, g.remainingMs("x"))
    }

    @Test fun clockBackDoesNotRewindDay() {
        val g = guard()
        g.onDetection("x")
        clock.wall -= 2 * 86_400_000L
        g.tick()
        assertEquals(LocalDate.parse("2026-10-04"), g.today.date)
        assertEquals(1, g.today.attempts)
        clock.advance(14 * 3_600_000L)                        // tempo real até 00:00 do dia 5
        g.tick()
        assertEquals(LocalDate.parse("2026-10-05"), g.today.date)
    }

    @Test fun timezoneWestNeverRewindsDate() {
        clock = FakeClock("2026-10-05T00:30")
        val g = guard()
        g.onDetection("x")
        clock.zoneId = ZoneId.of("America/Manaus")            // agora são 23:30 do dia 4
        g.tick()
        assertEquals(LocalDate.parse("2026-10-05"), g.today.date)
        assertEquals(1, g.today.attempts)
    }

    @Test fun cappedByPolicy() {
        val g = guard { Policy(maxBlock = 1.minutes) }
        (1..20).forEach { g.onDetection("app$it") }
        (4..20).forEach { assertTrue(g.remainingMs("app$it") <= 60_000L) }
        assertEquals(60_000L, g.remainingMs("app20"))
    }

    @Test fun policyChangeAppliesToNextBlockOnly() {
        var policy = Policy()
        val g = guard { policy }
        g.reachBlock("x")                                     // 30 s
        policy = Policy(initialBlock = 60.seconds)
        assertEquals(30_000L, g.remainingMs("x"))
        g.onDetection("y")                                    // tentativa 5 → 60 s × 2
        assertEquals(120_000L, g.remainingMs("y"))
    }

    @Test fun corruptStateStartsFresh() {
        saved = mapOf("today" to "lixo", "block:x" to "1,2", "anchorWall" to "abc", "history" to ";;x")
        val g = guard()
        assertEquals(0, g.today.attempts)
        assertEquals(0L, g.remainingMs("x"))
        assertEquals(Decision.Warn(1), g.onDetection("x"))
    }

    @Test fun lastDaysFillsGaps() {
        val g = guard()
        g.onDetection("x")
        clock.advance(3 * 86_400_000L)
        g.tick()
        val days = g.lastDays()
        assertEquals(7, days.size)
        assertEquals(LocalDate.parse("2026-10-07"), days.last().date)
        assertEquals(LocalDate.parse("2026-10-04"), days[3].date)
        assertEquals(1, days[3].attempts)
        assertEquals(0, days.last().attempts)
    }
}
