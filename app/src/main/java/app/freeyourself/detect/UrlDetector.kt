package app.freeyourself.detect

import java.text.Normalizer

/**
 * Reconhece sites adultos pelo endereço. Recebe só o texto da barra de endereço do navegador,
 * nunca mensagens ou páginas: uma palavra que alguém escreva para você não dispara nada.
 * [rules] segue o formato de assets/blocklist.txt.
 */
class UrlDetector(rules: String) : ContentDetector {
    private val domains = HashSet<String>()
    private val hostTokens = ArrayList<String>()

    init {
        var section = ""
        rules.lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }.forEach { line ->
            if (line.startsWith("[")) section = line
            else when (section) {
                "[domains]" -> domains += normalize(line)
                "[host-tokens]" -> hostTokens += normalize(line)
            }
        }
    }

    override fun isAdult(frame: ScreenFrame) = frame.text?.let(::matches) ?: false

    fun matches(raw: String): Boolean = HOST.findAll(normalize(raw)).any { isAdultHost(it.value) }

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
        /** Só o host: o caminho e a busca (`?q=…`) são texto livre e não contam. */
        val HOST = Regex("(?<![a-z0-9.-])(?:[a-z0-9-]+\\.)+[a-z]{2,}")
    }
}

private val ADDRESS_BAR = Regex("url_bar|url_field|url_view|location_bar|omnibar|address_bar|addressbar")

/** ID da view da barra de endereço nos navegadores comuns (Chrome, Firefox, Samsung, Edge, Opera, DuckDuckGo…). */
fun isAddressBarId(id: String?): Boolean =
    id != null && ADDRESS_BAR.containsMatchIn(id.substringAfter(":id/").lowercase())

private val MARKS = Regex("\\p{Mn}+")

fun normalize(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()
