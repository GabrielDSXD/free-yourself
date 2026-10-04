package app.freeyourself.ui

import app.freeyourself.core.DayStats
import app.freeyourself.core.Policy
import app.freeyourself.core.getBlockDuration
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

const val PRIVACY_TEXT =
    "O conteúdo da tela é analisado no próprio aparelho, em memória, e descartado em seguida. " +
        "O Free Yourself não grava capturas, imagens, sites ou textos: guarda só os contadores de cada dia. " +
        "O app não tem permissão de acesso à internet, então nada pode ser enviado."

/** "30 s", "8 min", "1 h 4 min", com espaço inseparável entre número e unidade. */
fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "$s\u00A0s"
        s < 3600 -> "${s / 60}\u00A0min"
        else -> {
            val m = s % 3600 / 60
            if (m == 0L) "${s / 3600}\u00A0h" else "${s / 3600}\u00A0h $m\u00A0min"
        }
    }
}

/** Contador regressivo: "01:42" ou "1:02:05" (arredonda para cima). */
fun formatClock(ms: Long): String {
    val s = (ms + 999) / 1000
    val two = { n: Long -> n.toString().padStart(2, '0') }
    return if (s >= 3600) "${s / 3600}:${two(s % 3600 / 60)}:${two(s % 60)}" else "${two(s / 60)}:${two(s % 60)}"
}

/** Texto para leitores de tela; muda no máximo uma vez por minuto. */
fun remainingLabel(ms: Long): String =
    if (ms < 60_000) "Falta menos de 1 minuto" else "Faltam ${(ms + 59_999) / 60_000} minutos"

fun greeting(hour: Int) = when (hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

fun nextStep(day: DayStats, p: Policy): String {
    val left = p.warnings - day.attempts
    return when {
        left > 1 -> "$left avisos antes do primeiro bloqueio."
        left == 1 -> "1 aviso antes do primeiro bloqueio."
        else -> "Uma nova detecção hoje bloqueia o app por " +
            formatDuration(getBlockDuration(day.attempts + 1, p).inWholeMilliseconds) + "."
    }
}

fun sequenceText(p: Policy): String {
    val first = p.warnings + 1
    val steps = (first until first + 4).joinToString(", ") { formatDuration(getBlockDuration(it, p).inWholeMilliseconds) }
    return "A partir da ${first}ª detecção do dia: $steps… até ${formatDuration(p.maxBlock.inWholeMilliseconds)}."
}

fun weekday(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, PT_BR).removeSuffix(".").replaceFirstChar { it.uppercase() }

/** Até o reset dos contadores: a meia-noite que encerra o dia do contador, não o dia do relógio. */
fun msUntilReset(now: ZonedDateTime, today: LocalDate): Long =
    Duration.between(now, today.plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(0)

fun blockFootnote(attempts: Int): String =
    if (attempts == 0) "Este bloqueio começou ontem. Os contadores de hoje já recomeçaram."
    else "Hoje: ${attempts}ª detecção. À meia-noite tudo recomeça."
