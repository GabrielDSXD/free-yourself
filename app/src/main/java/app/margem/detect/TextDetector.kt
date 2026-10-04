package app.margem.detect

import java.text.Normalizer

/**
 * Procura domínios e termos explícitos no texto visível, o que inclui a barra de endereço
 * de qualquer navegador e o texto de buscas. [rules] segue o formato de assets/blocklist.txt.
 */
class TextDetector(rules: String) : ContentDetector {
    private val domains = HashSet<String>()
    private val hostTokens = ArrayList<String>()
    private val terms: Regex

    init {
        val words = ArrayList<String>()
        var section = ""
        rules.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            if (line.startsWith("[")) section = line
            else when (section) {
                "[domains]" -> domains += normalize(line)
                "[host-tokens]" -> hostTokens += normalize(line)
                "[terms]" -> words += Regex.escape(normalize(line))
            }
        }
        terms = Regex("\\b(?:" + words.joinToString("|") + ")\\b")
    }

    override fun isAdult(frame: ScreenFrame) = frame.text?.let(::matches) ?: false

    fun matches(raw: String): Boolean {
        val text = normalize(raw)
        return terms.containsMatchIn(text) || HOST.findAll(text).any { isAdultHost(it.value) }
    }

    private fun isAdultHost(host: String): Boolean {
        if (hostTokens.any { it in host }) return true
        var h = host
        while ('.' in h) {
            if (h in domains) return true
            h = h.substringAfter('.')
        }
        return false
    }

    private companion object {
        val HOST = Regex("(?:[a-z0-9-]+\\.)+[a-z]{2,}")
    }
}

private val MARKS = Regex("\\p{Mn}+")

fun normalize(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
