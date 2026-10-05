package app.freeyourself.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudeNetTest {
    private val anchors = 2100

    /** Saída no formato do YOLOv8: 4 linhas de caixa + 18 linhas de classe, uma coluna por âncora. */
    private fun output(vararg scores: Pair<String, Float>): Array<FloatArray> {
        val out = Array(4 + NUDENET_LABELS.size) { FloatArray(anchors) }
        scores.forEachIndexed { anchor, (label, score) -> out[4 + NUDENET_LABELS.indexOf(label)][anchor] = score }
        return out
    }

    @Test fun eighteenLabelsInModelOrder() {
        assertEquals(18, NUDENET_LABELS.size)
        assertEquals("FEMALE_GENITALIA_COVERED", NUDENET_LABELS.first())
        assertEquals("BUTTOCKS_COVERED", NUDENET_LABELS.last())
    }

    @Test fun nothingDetected() {
        assertFalse(exposedFound(output(), includeCovered = false))
    }

    @Test fun exposedPartCounts() {
        assertTrue(exposedFound(output("FEMALE_BREAST_EXPOSED" to 0.6f), includeCovered = false))
        assertTrue(exposedFound(output("MALE_GENITALIA_EXPOSED" to 0.5f), includeCovered = false))
    }

    /** Rostos, pés, barriga e axilas não são conteúdo adulto: jogos e animes têm muito disso. */
    @Test fun nonSexualPartsNeverCount() {
        val out = output("FACE_FEMALE" to 0.95f, "FEET_EXPOSED" to 0.9f, "BELLY_EXPOSED" to 0.9f, "ARMPITS_EXPOSED" to 0.9f)
        assertFalse(exposedFound(out, includeCovered = true))
    }

    @Test fun lowConfidenceDoesNotCount() {
        assertFalse(exposedFound(output("FEMALE_GENITALIA_EXPOSED" to 0.3f), includeCovered = false))
    }

    /** Sensibilidade Alta: lingerie / partes cobertas também valem. */
    @Test fun coveredOnlyWhenAsked() {
        val lingerie = output("FEMALE_BREAST_COVERED" to 0.7f)
        assertFalse(exposedFound(lingerie, includeCovered = false))
        assertTrue(exposedFound(lingerie, includeCovered = true))
    }
}
