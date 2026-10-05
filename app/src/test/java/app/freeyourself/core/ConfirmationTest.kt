package app.freeyourself.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmationTest {
    private val c = Confirmation(windowMs = 6_000)

    /** Caso real: um quadro de luta de anime lido como 73% hentai, cercado de quadros "desenho". */
    @Test fun singleFrameNeverTriggers() {
        assertFalse(c.onFrame("yt", adult = true, now = 0))
        assertFalse(c.onFrame("yt", adult = false, now = 5_000))
        assertFalse(c.onFrame("yt", adult = true, now = 10_000))
    }

    @Test fun secondPositiveConfirms() {
        assertFalse(c.onFrame("gallery", adult = true, now = 0))
        assertTrue(c.onFrame("gallery", adult = true, now = 1_100))
    }

    @Test fun positivesTooFarApartDoNotConfirm() {
        assertFalse(c.onFrame("yt", adult = true, now = 0))
        assertFalse(c.onFrame("yt", adult = true, now = 7_000))
        assertTrue(c.onFrame("yt", adult = true, now = 8_000))   // 7 s e 8 s confirmam entre si
    }

    @Test fun otherAppDoesNotConfirm() {
        assertFalse(c.onFrame("a", adult = true, now = 0))
        assertFalse(c.onFrame("b", adult = true, now = 1_000))
    }

    @Test fun startsOverAfterConfirming() {
        c.onFrame("x", adult = true, now = 0)
        assertTrue(c.onFrame("x", adult = true, now = 1_000))
        assertFalse(c.onFrame("x", adult = true, now = 2_000))
    }
}
