package app.freeyourself.detect

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.freeyourself.core.Sensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDetectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun neutralScreenIsNotAdult() {
        ImageDetector(context) { Sensitivity.HIGH }.use { detector ->
            val screen = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            screen.eraseColor(Color.rgb(238, 241, 244))
            val hardware = screen.copy(Bitmap.Config.HARDWARE, false)   // igual ao screenshot real
            val p = detector.classify(hardware)
            assertEquals(5, p.size)
            assertEquals(1f, p.sum(), 0.02f)
            assertFalse(detector.isAdult(ScreenFrame("test", bitmap = hardware)))
            // O NudeNet roda (formato de entrada/saída certo) e não vê nada numa tela lisa, em pé ou deitada.
            assertFalse(detector.bodyPartsFound(hardware, includeCovered = true))
            val landscape = Bitmap.createBitmap(2400, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.DKGRAY) }
            assertFalse(detector.bodyPartsFound(landscape, includeCovered = true))
        }
    }
}
