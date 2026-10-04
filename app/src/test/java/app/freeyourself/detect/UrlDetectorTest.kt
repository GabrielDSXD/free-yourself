package app.freeyourself.detect

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDetectorTest {
    private val detector = UrlDetector(File("src/main/assets/blocklist.txt").readText())

    @Test fun detectsKnownDomainsAndSubdomains() {
        assertTrue(detector.matches("https://www.pornhub.com/view_video.php"))
        assertTrue(detector.matches("m.xvideos.com"))
        assertTrue(detector.matches("pt.xhamster.desi/videos"))
        assertTrue(detector.matches("beeg.com"))
        assertTrue(detector.matches("www.xvideos.red"))
        assertTrue(detector.matches("pt.cam4.com.br"))
        assertTrue(detector.matches("www.porntube.com"))
        assertTrue(detector.matches("motherless.com/term/videos"))
        assertTrue(detector.matches("www.xozilla.com"))
        assertTrue(detector.matches("redgifs.com/watch/x"))
    }

    /** Qualquer pessoa consegue mandar uma palavra; só um site adulto aberto conta. */
    @Test fun looseWordsNeverMatch() {
        val words = listOf(
            "nsfw", "Pornô grátis", "sexo explícito", "Watch HENTAI online", "rule34",
            "google.com/search?q=videos+porno", "www.google.com/search?q=nsfw",
        )
        words.forEach { assertFalse(it, detector.matches(it)) }
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

    @Test fun recognizesBrowserAddressBars() {
        listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.duckduckgo.mobile.android:id/omnibarTextInput",
            "com.opera.browser:id/url_field",
            "com.microsoft.emmx:id/url_bar",
        ).forEach { assertTrue(it, isAddressBarId(it)) }
        listOf(null, "com.android.chrome:id/search_box_text", "com.android.chrome:id/title", "com.whatsapp:id/message_text")
            .forEach { assertFalse("$it", isAddressBarId(it)) }
    }
}
