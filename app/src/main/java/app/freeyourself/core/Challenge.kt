package app.freeyourself.core

import java.text.Normalizer

/** Digitadas uma a uma antes de afrouxar a proteção pelo app. Edite aqui para mudar o desafio. */
val CHALLENGE_PHRASES = listOf(
    "Eu instalei este app para proteger minha mente",
    "Senhor, me desculpe por pecar",
    "Eu sou fraco demais para resistir à tentação da luxúria",
    "Esse impulso vai passar se eu esperar",
    "Estou desistindo da decisão que tomei por mim",
    "Eu sei que vou me arrepender depois",
    "Eu escolho afrouxar a proteção mesmo assim",
)

private val MARKS = Regex("\\p{Mn}+")
private val NOT_WORD = Regex("[^a-z0-9 ]")
private val SPACES = Regex("\\s+")

/** Minúsculas, sem acentos, sem pontuação, espaços simples. */
private fun simplify(s: String): String {
    val plain = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
    return SPACES.replace(NOT_WORD.replace(plain, " "), " ").trim()
}

/** A frase precisa estar inteira; só forma (maiúsculas, acentos, pontuação, espaços) é tolerada. */
fun phraseMatches(typed: String, expected: String): Boolean = simplify(typed) == simplify(expected)
