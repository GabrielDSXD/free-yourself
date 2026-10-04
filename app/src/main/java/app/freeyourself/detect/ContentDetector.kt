package app.freeyourself.detect

import android.graphics.Bitmap

/** O que estava na tela num instante. Vive só em memória; nunca é gravado. */
class ScreenFrame(val pkg: String, val text: String? = null, val bitmap: Bitmap? = null)

/** Um método de detecção. Cada implementação ignora o frame se o campo que usa for nulo. */
fun interface ContentDetector {
    fun isAdult(frame: ScreenFrame): Boolean
}
