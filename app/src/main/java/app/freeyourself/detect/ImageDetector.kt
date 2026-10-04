package app.freeyourself.detect

import android.content.Context
import android.graphics.Bitmap
import app.freeyourself.core.Sensitivity
import app.freeyourself.core.isAdultImage
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import org.tensorflow.lite.Interpreter

/**
 * Classificador NSFW on-device (GantMan MobileNetV2, MIT). Entrada 224×224 RGB em [0,1].
 * ponytail: classifica a tela inteira; miniaturas pequenas se diluem. Evolução: recortar pelos
 * bounds dos nós de imagem da árvore de acessibilidade.
 */
class ImageDetector(context: Context, private val sensitivity: () -> Sensitivity) : ContentDetector, Closeable {
    private val interpreter: Interpreter = context.assets.openFd(MODEL).use { fd ->
        FileInputStream(fd.fileDescriptor).channel.use { channel ->
            val model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            Interpreter(model, Interpreter.Options().setNumThreads(2))
        }
    }
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)
    private val output = Array(1) { FloatArray(5) }

    /** Resultado de uma captura: conteúdo adulto? captura preta (janela protegida)? */
    class Inspection(val adult: Boolean, val blank: Boolean)

    override fun isAdult(frame: ScreenFrame): Boolean = frame.bitmap?.let { inspect(it).adult } ?: false

    @Synchronized
    fun inspect(bitmap: Bitmap): Inspection {
        val probs = classify(bitmap)
        return Inspection(adult = isAdultImage(probs, sensitivity()), blank = isBlankFrame(pixels))
    }

    /** Probabilidades na ordem drawings, hentai, neutral, porn, sexy. Não retém o bitmap. */
    @Synchronized
    fun classify(bitmap: Bitmap): FloatArray {
        val small = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val soft = if (small.config == Bitmap.Config.HARDWARE) small.copy(Bitmap.Config.ARGB_8888, false) else small
        soft.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        listOf(small, soft).distinct().filter { it !== bitmap }.forEach(Bitmap::recycle)
        input.rewind()
        for (c in pixels) {
            input.putFloat((c shr 16 and 0xFF) / 255f)
            input.putFloat((c shr 8 and 0xFF) / 255f)
            input.putFloat((c and 0xFF) / 255f)
        }
        interpreter.run(input, output)
        return output[0].copyOf()
    }

    override fun close() = interpreter.close()

    private companion object {
        const val MODEL = "nsfw.tflite"
        const val SIZE = 224
    }
}
