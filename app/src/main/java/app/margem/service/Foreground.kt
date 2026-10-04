package app.margem.service

/**
 * Pacote que passa a estar em primeiro plano após um TYPE_WINDOW_STATE_CHANGED, ou null se a janela
 * aparece por cima sem trocar o app (barra de notificações, teclado, os overlays do próprio serviço).
 * O próprio app só conta quando é a Activity dele, para não confundir com os overlays.
 */
internal fun foregroundAfter(
    pkg: String,
    className: String?,
    ownPackage: String,
    ownActivity: String,
    passthrough: Set<String>,
): String? = when {
    pkg == ownPackage -> pkg.takeIf { className == ownActivity }
    pkg in passthrough -> null
    else -> pkg
}
