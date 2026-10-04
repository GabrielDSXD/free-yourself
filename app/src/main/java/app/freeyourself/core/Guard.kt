package app.freeyourself.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

data class DayStats(val date: LocalDate, val warnings: Int = 0, val blocks: Int = 0, val blockedMs: Long = 0) {
    val attempts: Int get() = warnings + blocks
}

sealed interface Decision {
    data object Ignore : Decision
    data class Warn(val level: Int) : Decision
    data object Block : Decision
}

/**
 * Regras do dia: conta tentativas, decide aviso ou bloqueio, vira o dia e guarda bloqueios ativos.
 *
 * O "agora" é confiável: ancorado no relógio monotônico dentro de um boot, então mudar o relógio
 * do aparelho não zera o dia nem encurta bloqueios. Após reboot a âncora é refeita sem nunca voltar
 * antes do último instante salvo.
 * ponytail: mudar o relógio E reiniciar burla o reset; fechar isso exigiria hora de rede (sem INTERNET).
 *
 * [saved]/[onChange]: estado serializado (chave → valor) para persistência.
 */
class Guard(
    private val clock: Clock,
    private val policy: () -> Policy = { Policy() },
    saved: Map<String, String> = emptyMap(),
    private val onChange: (Map<String, String>) -> Unit = {},
) {
    private class ActiveBlock(val endElapsed: Long, val endWall: Long, val durationMs: Long)

    private val anchorBoot = clock.bootCount()
    private val anchorWall: Long
    private val anchorElapsed: Long
    private var lastTrusted: Long
    private val blocks = HashMap<String, ActiveBlock>()
    private var quietUntil = Long.MIN_VALUE

    var today: DayStats
        private set

    /** Dias anteriores registrados, do mais recente ao mais antigo (no máximo 6). */
    var history: List<DayStats> = emptyList()
        private set

    init {
        val savedWall = saved["anchorWall"]?.toLongOrNull()
        val savedElapsed = saved["anchorElapsed"]?.toLongOrNull()
        lastTrusted = saved["lastTrusted"]?.toLongOrNull() ?: clock.wallMs()
        val sameBoot = saved["anchorBoot"]?.toIntOrNull() == anchorBoot &&
            savedWall != null && savedElapsed != null && savedElapsed <= clock.elapsedMs()
        if (sameBoot) {
            anchorWall = savedWall!!
            anchorElapsed = savedElapsed!!
        } else {
            anchorWall = maxOf(clock.wallMs(), lastTrusted)
            anchorElapsed = clock.elapsedMs()
        }
        val now = trustedNow()
        today = saved["today"]?.let(::parseDay) ?: DayStats(dateOf(now))
        history = saved["history"].orEmpty().split(';').mapNotNull(::parseDay)
        for ((key, value) in saved) {
            if (!key.startsWith("block:")) continue
            runCatching {
                val (endElapsed, endWall, duration) = value.split(',').map(String::toLong)
                val end = if (sameBoot) endElapsed else clock.elapsedMs() + (endWall - now).coerceIn(0, duration)
                blocks[key.removePrefix("block:")] = ActiveBlock(end, endWall, duration)
            }
        }
        tick()
    }

    fun now(): ZonedDateTime = Instant.ofEpochMilli(trustedNow()).atZone(clock.zone())

    /** Vira o dia se preciso e encerra bloqueios vencidos. Retorna os pacotes cujo bloqueio terminou. */
    fun tick(): List<String> {
        val date = dateOf(trustedNow())
        val rolled = date > today.date
        if (rolled) {
            history = (listOf(today) + history).take(6)
            today = DayStats(date)
        }
        val elapsed = clock.elapsedMs()
        val ended = blocks.filterValues { it.endElapsed <= elapsed }.keys.toList()
        ended.forEach(blocks::remove)
        if (rolled || ended.isNotEmpty()) save()
        return ended
    }

    fun remainingMs(pkg: String): Long =
        blocks[pkg]?.let { (it.endElapsed - clock.elapsedMs()).coerceAtLeast(0) } ?: 0

    fun isQuiet() = clock.elapsedMs() < quietUntil

    fun onDetection(pkg: String): Decision {
        tick()
        if (remainingMs(pkg) > 0) return Decision.Block
        if (isQuiet()) return Decision.Ignore
        val p = policy()
        val attempt = today.attempts + 1
        if (attempt <= p.warnings) {
            today = today.copy(warnings = today.warnings + 1)
            save()
            return Decision.Warn(attempt)
        }
        val d = getBlockDuration(attempt, p).inWholeMilliseconds
        blocks[pkg] = ActiveBlock(clock.elapsedMs() + d, trustedNow() + d, d)
        today = today.copy(blocks = today.blocks + 1, blockedMs = today.blockedMs + d)
        save()
        return Decision.Block
    }

    /** "Continuar mesmo assim": a próxima detecção só conta depois da carência. */
    fun onContinue() {
        quietUntil = clock.elapsedMs() + policy().grace.inWholeMilliseconds
    }

    /** Usuário saiu do conteúdo: dá tempo da tela anterior sumir. */
    fun onLeave() {
        quietUntil = maxOf(quietUntil, clock.elapsedMs() + policy().settle.inWholeMilliseconds)
    }

    fun lastDays(n: Int = 7): List<DayStats> {
        val byDate = (history + today).associateBy { it.date }
        return (n - 1 downTo 0).map { back ->
            val d = today.date.minusDays(back.toLong())
            byDate[d] ?: DayStats(d)
        }
    }

    fun snapshot(): Map<String, String> = buildMap {
        put("anchorWall", "$anchorWall")
        put("anchorElapsed", "$anchorElapsed")
        put("anchorBoot", "$anchorBoot")
        put("lastTrusted", "$lastTrusted")
        put("today", encode(today))
        put("history", history.joinToString(";", transform = ::encode))
        blocks.forEach { (pkg, b) -> put("block:$pkg", "${b.endElapsed},${b.endWall},${b.durationMs}") }
    }

    private fun trustedNow() = anchorWall + (clock.elapsedMs() - anchorElapsed)

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(clock.zone()).toLocalDate()

    private fun save() {
        lastTrusted = maxOf(lastTrusted, trustedNow())
        onChange(snapshot())
    }

    private fun encode(d: DayStats) = "${d.date},${d.warnings},${d.blocks},${d.blockedMs}"

    private fun parseDay(s: String): DayStats? = runCatching {
        val p = s.split(',')
        DayStats(LocalDate.parse(p[0]), p[1].toInt(), p[2].toInt(), p[3].toLong())
    }.getOrNull()
}
