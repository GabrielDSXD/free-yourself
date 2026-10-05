package app.freeyourself.detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import app.freeyourself.core.Sensitivity
import app.freeyourself.core.isAdultImage
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import org.tensorflow.lite.Interpreter

/**
 * Dois modelos on-device. O classificador NSFW (GantMan MobileNetV2, MIT) olha a tela inteira;
 * quando ele acusa, o NudeNet (detector de partes do corpo, AGPL-3.0) confirma. Cores de pele e
 * cenas de luta enganam o primeiro; o segundo só aceita parte íntima exposta.
 * ponytail: classifica a tela inteira; miniaturas pequenas se diluem. Evolução: recortar pelos
 * bounds dos nós de imagem da árvore de acessibilidade.
 */
class ImageDetector(context: Context, private val sensitivity: () -> Sensitivity) : ContentDetector, Closeable {
    private val classifier = load(context, MODEL)
    private val nudeNet = load(context, NUDENET_MODEL)
    private val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val pixels = IntArray(SIZE * SIZE)
    private val output = Array(1) { FloatArray(5) }
    private val nudeInput = ByteBuffer.allocateDirect(NUDENET_SIZE * NUDENET_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val nudePixels = IntArray(NUDENET_SIZE * NUDENET_SIZE)
    private val nudeOutput = Array(1) { Array(4 + NUDENET_LABELS.size) { FloatArray(NUDENET_ANCHORS) } }

    /** Resultado de uma captura: conteúdo adulto? captura preta (janela protegida)? */
    class Inspection(val adult: Boolean, val blank: Boolean)

    override fun isAdult(frame: ScreenFrame): Boolean = frame.bitmap?.let { inspect(it).adult } ?: false

    @Synchronized
    fun inspect(bitmap: Bitmap): Inspection {
        val s = sensitivity()
        val probs = classify(bitmap)
        val blank = isBlankFrame(pixels)
        val adult = isAdultImage(probs, s) && bodyPartsFound(bitmap, includeCovered = s == Sensitivity.HIGH)
        return Inspection(adult, blank)
    }

    /** Probabilidades na ordem drawings, hentai, neutral, porn, sexy. Não retém o bitmap. */
    @Synchronized
    fun classify(bitmap: Bitmap): FloatArray {
        sample(bitmap, 0, 0, bitmap.width, bitmap.height, SIZE, pixels)
        fill(input, pixels)
        classifier.run(input, output)
        return output[0].copyOf()
    }

    /**
     * NudeNet em quadrados sobrepostos ao longo do lado maior (3 numa tela em pé): o vídeo no topo
     * de uma tela vertical ficaria pequeno demais se a tela inteira fosse espremida em 320 px.
     */
    internal fun bodyPartsFound(bitmap: Bitmap, includeCovered: Boolean): Boolean {
        val side = min(bitmap.width, bitmap.height)
        val long = max(bitmap.width, bitmap.height)
        val tiles = ceil(long.toFloat() / side).toInt()
        return (0 until tiles).any { i ->
            val offset = if (tiles == 1) 0 else (long - side) * i / (tiles - 1)
            val (x, y) = if (bitmap.height >= bitmap.width) 0 to offset else offset to 0
            sample(bitmap, x, y, side, side, NUDENET_SIZE, nudePixels)
            fill(nudeInput, nudePixels)
            nudeNet.run(nudeInput, nudeOutput)
            exposedFound(nudeOutput[0], includeCovered)
        }
    }

    /** Reduz a região do bitmap para size×size e lê os pixels. Não retém nada. */
    private fun sample(bitmap: Bitmap, x: Int, y: Int, w: Int, h: Int, size: Int, into: IntArray) {
        val scale = Matrix().apply { setScale(size.toFloat() / w, size.toFloat() / h) }
        val small = Bitmap.createBitmap(bitmap, x, y, w, h, scale, true)
        val soft = if (small.config == Bitmap.Config.HARDWARE) small.copy(Bitmap.Config.ARGB_8888, false) else small
        soft.getPixels(into, 0, size, 0, 0, size, size)
        listOf(small, soft).distinct().filter { it !== bitmap }.forEach(Bitmap::recycle)
    }

    /** RGB em [0,1], o formato que os dois modelos esperam. */
    private fun fill(buffer: ByteBuffer, from: IntArray) {
        buffer.rewind()
        for (c in from) {
            buffer.putFloat((c shr 16 and 0xFF) / 255f)
            buffer.putFloat((c shr 8 and 0xFF) / 255f)
            buffer.putFloat((c and 0xFF) / 255f)
        }
    }

    override fun close() {
        classifier.close()
        nudeNet.close()
    }

    private companion object {
        const val MODEL = "nsfw.tflite"
        const val SIZE = 224
        const val NUDENET_MODEL = "nudenet.tflite"
        const val NUDENET_SIZE = 320
        const val NUDENET_ANCHORS = 2100

        fun load(context: Context, name: String): Interpreter = context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                val model = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                Interpreter(model, Interpreter.Options().setNumThreads(2))
            }
        }
    }
}
