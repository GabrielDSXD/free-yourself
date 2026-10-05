package app.freeyourself.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengeTest {
    @Test fun sevenPhrases() {
        assertEquals(7, CHALLENGE_PHRASES.size)
        assertTrue(CHALLENGE_PHRASES.all { it.isNotBlank() })
    }

    @Test fun ignoresCaseAccentsPunctuationAndSpacing() {
        val expected = "Eu sou fraco demais para resistir à tentação da luxúria"
        assertTrue(phraseMatches("eu sou fraco demais para resistir a tentacao da luxuria", expected))
        assertTrue(phraseMatches("  EU SOU FRACO   demais para resistir à tentação da luxúria.  ", expected))
        assertTrue(phraseMatches("Senhor me desculpe por pecar!", "Senhor, me desculpe por pecar"))
    }

    @Test fun requiresTheWholePhrase() {
        val expected = "Eu instalei este app para proteger minha mente"
        assertFalse(phraseMatches("Eu instalei este app", expected))
        assertFalse(phraseMatches("Eu instalei este app para proteger minha mente agora", expected))
        assertFalse(phraseMatches("", expected))
    }
}
