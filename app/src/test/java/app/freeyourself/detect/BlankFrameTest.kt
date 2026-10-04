package app.freeyourself.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankFrameTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private val black = rgb(0, 0, 0)
    private val white = rgb(255, 255, 255)

    @Test fun fullyBlackIsBlank() {
        assertEquals(1f, blankFraction(IntArray(100) { black }), 0.001f)
        assertTrue(isBlankFrame(IntArray(100) { black }))
    }

    /** Captura de aba anônima: tudo preto menos a barra de status do sistema. */
    @Test fun blackWithStatusBarIsBlank() {
        val pixels = IntArray(100) { if (it < 5) white else black }
        assertTrue(isBlankFrame(pixels))
    }

    @Test fun darkThemePageIsNotBlank() {
        val toolbarGray = rgb(0x20, 0x21, 0x24)   // barra do Chrome no tema escuro
        assertFalse(isBlankFrame(IntArray(100) { toolbarGray }))
    }

    @Test fun halfBlackIsNotBlank() {
        assertFalse(isBlankFrame(IntArray(100) { if (it % 2 == 0) white else black }))
    }
}
