package app.margem.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTest {
    private val own = "app.margem"
    private val activity = "app.margem.ui.MainActivity"
    private val passthrough = setOf("com.android.systemui", "com.google.android.inputmethod.latin")
    private fun after(pkg: String, className: String? = null) = foregroundAfter(pkg, className, own, activity, passthrough)

    @Test fun otherAppsBecomeForeground() {
        assertEquals("com.android.chrome", after("com.android.chrome", "org.chromium.chrome.browser.ChromeTabbedActivity"))
    }

    @Test fun overlaysAndKeyboardDoNotChangeForeground() {
        assertNull(after("com.android.systemui"))
        assertNull(after("com.google.android.inputmethod.latin"))
        assertNull(after(own, "android.widget.FrameLayout"))      // janela de overlay do próprio serviço
    }

    @Test fun openingMargemItselfIsAForegroundChange() {
        assertEquals(own, after(own, activity))
    }
}
