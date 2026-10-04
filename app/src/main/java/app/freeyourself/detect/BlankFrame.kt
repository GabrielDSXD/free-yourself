package app.freeyourself.detect

/**
 * O Android entrega preta a captura de janelas protegidas (FLAG_SECURE), como abas anônimas.
 * Uma captura "em branco" de navegador = modo privado que o app não consegue analisar.
 */
fun blankFraction(pixels: IntArray): Float {
    if (pixels.isEmpty()) return 0f
    val dark = pixels.count { c ->
        val luma = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000
        luma < 16
    }
    return dark.toFloat() / pixels.size
}

/** ≥ 90% preto: sobra espaço para a barra de status, que o sistema ainda desenha. */
fun isBlankFrame(pixels: IntArray): Boolean = blankFraction(pixels) >= 0.9f
