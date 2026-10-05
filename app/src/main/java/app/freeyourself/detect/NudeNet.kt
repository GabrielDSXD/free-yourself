package app.freeyourself.detect

/** Classes do NudeNet v3 (notAI-tech/NudeNet, AGPL-3.0), na ordem da saída do modelo. */
val NUDENET_LABELS = listOf(
    "FEMALE_GENITALIA_COVERED", "FACE_FEMALE", "BUTTOCKS_EXPOSED", "FEMALE_BREAST_EXPOSED",
    "FEMALE_GENITALIA_EXPOSED", "MALE_BREAST_EXPOSED", "ANUS_EXPOSED", "FEET_EXPOSED",
    "BELLY_COVERED", "FEET_COVERED", "ARMPITS_COVERED", "ARMPITS_EXPOSED",
    "FACE_MALE", "BELLY_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_COVERED",
    "FEMALE_BREAST_COVERED", "BUTTOCKS_COVERED",
)

private val EXPOSED = setOf(
    "FEMALE_GENITALIA_EXPOSED", "MALE_GENITALIA_EXPOSED", "ANUS_EXPOSED", "FEMALE_BREAST_EXPOSED", "BUTTOCKS_EXPOSED",
)
private val COVERED = setOf("FEMALE_GENITALIA_COVERED", "FEMALE_BREAST_COVERED", "BUTTOCKS_COVERED", "ANUS_COVERED")
private val EXPOSED_ROWS = EXPOSED.map { 4 + NUDENET_LABELS.indexOf(it) }
private val COVERED_ROWS = COVERED.map { 4 + NUDENET_LABELS.indexOf(it) }

/**
 * [output] no formato do YOLOv8: 4 linhas de caixa + 18 de classe, uma coluna por âncora.
 * Basta a maior pontuação de uma classe sexual em qualquer âncora: não precisamos das caixas.
 * [includeCovered] = sensibilidade Alta (lingerie e partes cobertas também contam).
 */
fun exposedFound(output: Array<FloatArray>, includeCovered: Boolean, threshold: Float = 0.4f): Boolean {
    val rows = if (includeCovered) EXPOSED_ROWS + COVERED_ROWS else EXPOSED_ROWS
    return rows.any { row -> output[row].any { it >= threshold } }
}
