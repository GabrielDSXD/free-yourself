package app.freeyourself.core

import java.time.LocalDateTime
import java.time.ZoneId

class FakeClock(start: String = "2026-10-04T10:00") : Clock {
    var zoneId: ZoneId = ZoneId.of("America/Sao_Paulo")
    var wall = LocalDateTime.parse(start).atZone(zoneId).toInstant().toEpochMilli()
    var elapsed = 3_600_000L
    var boot = 1

    /** Tempo real passando: os dois relógios andam juntos. */
    fun advance(ms: Long) { wall += ms; elapsed += ms }

    /** Aparelho desligado por [offMs] e religado. */
    fun reboot(offMs: Long) { wall += offMs; elapsed = 5_000; boot++ }

    override fun wallMs() = wall
    override fun elapsedMs() = elapsed
    override fun bootCount() = boot
    override fun zone() = zoneId
}
