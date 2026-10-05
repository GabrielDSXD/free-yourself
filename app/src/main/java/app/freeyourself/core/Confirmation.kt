package app.freeyourself.core

/**
 * Um quadro positivo isolado não basta: precisa de outro positivo, do mesmo app, logo depois.
 * Conteúdo explícito de verdade continua na tela; um quadro de anime ou de luta lido errado, não.
 */
class Confirmation(private val windowMs: Long = 6_000) {
    private var pendingPkg: String? = null
    private var pendingAt = 0L

    /** Registra uma captura; true quando ela confirma um positivo anterior (e recomeça). */
    fun onFrame(pkg: String, adult: Boolean, now: Long): Boolean {
        if (!adult) {
            pendingPkg = null
            return false
        }
        val confirms = pendingPkg == pkg && now - pendingAt <= windowMs
        if (confirms) {
            pendingPkg = null
        } else {
            pendingPkg = pkg
            pendingAt = now
        }
        return confirms
    }
}
