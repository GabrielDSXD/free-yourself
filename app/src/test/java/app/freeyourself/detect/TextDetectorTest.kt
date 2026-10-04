package app.freeyourself.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDetectorTest {
    private val detector = TextDetector(File("src/main/assets/blocklist.txt").readText())

    @Test fun detectsKnownDomainsAndSubdomains() {
        assertTrue(detector.matches("https://www.pornhub.com/view_video.php"))
        assertTrue(detector.matches("m.xvideos.com"))
        assertTrue(detector.matches("pt.xhamster.desi/videos"))
        assertTrue(detector.matches("beeg.com"))
    }

    @Test fun detectsSearchesAndExplicitTerms() {
        assertTrue(detector.matches("google.com/search?q=videos+porno"))
        assertTrue(detector.matches("Pornô grátis"))
        assertTrue(detector.matches("Watch HENTAI online"))
        assertTrue(detector.matches("sexo explícito"))
    }

    @Test fun ignoresAmbiguousText() {
        val safe = listOf(
            "University of Essex", "Middlesex Hospital", "Sussex", "sexta-feira às 18h",
            "Moby Dick", "CPF xxx.xxx.xxx-xx", "educação sexual nas escolas",
            "batom nude", "Sexo do bebê: menina", "https://www.google.com/maps",
        )
        safe.forEach { assertFalse(it, detector.matches(it)) }
    }

    @Test fun normalizesAccentsAndCase() {
        assertEquals("porno explicito", normalize("PORNÔ Explícito"))
    }
}
