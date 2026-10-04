package app.freeyourself.core

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Todas as regras ajustáveis num só lugar. */
data class Policy(
    val warnings: Int = 3,
    val initialBlock: Duration = 30.seconds,
    val growthFactor: Double = 2.0,
    val maxBlock: Duration = 2.hours,
    /** Depois de "Continuar mesmo assim": detecções não contam por este tempo. */
    val grace: Duration = 30.seconds,
    /** Depois de "Voltar": tempo para a tela anterior sair sem gerar nova tentativa. */
    val settle: Duration = 3.seconds,
)

/** Duração do bloqueio para a [attempt]-ésima tentativa do dia; zero enquanto ainda é aviso. */
fun getBlockDuration(attempt: Int, policy: Policy = Policy()): Duration {
    val step = attempt - policy.warnings - 1
    if (step < 0) return Duration.ZERO
    val ms = policy.initialBlock.inWholeMilliseconds * policy.growthFactor.pow(step)
    return if (ms >= policy.maxBlock.inWholeMilliseconds) policy.maxBlock else ms.toLong().milliseconds
}

enum class Sensitivity { LOW, MEDIUM, HIGH }

/** [p] = saída do modelo na ordem drawings, hentai, neutral, porn, sexy. */
fun isAdultImage(p: FloatArray, s: Sensitivity): Boolean {
    val explicit = p[1] + p[3]
    return when (s) {
        Sensitivity.LOW -> explicit >= 0.85f
        Sensitivity.MEDIUM -> explicit >= 0.70f
        Sensitivity.HIGH -> explicit + 0.5f * p[4] >= 0.60f
    }
}
