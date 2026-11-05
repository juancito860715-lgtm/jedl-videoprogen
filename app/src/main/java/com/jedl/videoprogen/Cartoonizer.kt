package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

// ============================================================
// ESTILO ANIME REAL - White-box CartoonGAN (CVPR 2020) OFFLINE
// ============================================================
// Modelo: whitebox_cartoon_gan_fp16.tflite (3 MB, pesos fp16,
// entrada/salida float32 256x256x3, forma fija). Lo descarga el
// CI desde TensorFlow Hub (sayakpaul/lite-model/cartoogan/fp16/1)
// y lo empaqueta dentro del APK: 100% offline en el Redmi.
// Licencia Apache 2.0 (tutorial oficial de TensorFlow, pesos de
// los autores del paper) -> APTO PARA MONETIZAR.
// Fuentes: blog.tensorflow.org/2020/09/how-to-create-cartoonizer-
// with-tf-lite.html + github.com/margaretmz/cartoonizer-with-tflite
//
// FORMULAS EXACTAS (notebook oficial de conversion):
//   PRE:  float = pixel/127.5 - 1   (rango [-1, 1], canales BGR:
//         el modelo se entreno con OpenCV, que usa BGR)
//   POST: pixel = (float + 1) * 127.5   (clip 0..255, BGR->RGB)
//   ALPHA: la transparencia del recorte se conserva: el alpha de
//         cada pixel del resultado = alpha del pixel original.
// ============================================================
object Cartoonizer {

    private const val MODEL_ASSET = "whitebox_cartoon_gan_fp16.tflite"
    private const val SIZE = 256

    fun apply(context: Context, source: Bitmap): Bitmap? {
        // Cargar el modelo mapeado en memoria (sin copiarlo a la RAM)
        val interpreter = try {
            val afd = context.assets.openFd(MODEL_ASSET)
            val channel = afd.createInputStream().channel
            val bb = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            Interpreter(bb, Interpreter.Options().apply { setNumThreads(4) })
        } catch (e: Exception) { return null }

        return try {
            val w = source.width
            val h = source.height

            // --- PRE: estirar a 256x256 y aplanar sobre blanco
            //     (la IA espera una imagen RGB opaca)
            val scaled = Bitmap.createScaledBitmap(source, SIZE, SIZE, true)
            val base = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            val cnv = Canvas(base)
            cnv.drawColor(Color.WHITE)
            cnv.drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            if (scaled !== source) scaled.recycle()

            val pixels = IntArray(SIZE * SIZE)
            base.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            base.recycle()

            // [1,256,256,3] float32: por cada pixel B,G,R normalizados
            val input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
            for (c in pixels) {
                input.putFloat((Color.blue(c) / 127.5f) - 1f)
                input.putFloat((Color.green(c) / 127.5f) - 1f)
                input.putFloat((Color.red(c) / 127.5f) - 1f)
            }
            input.rewind()

            val output = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()

            // --- POST: BGR -> RGB, [-1,1] -> 0..255
            val outPixels = IntArray(SIZE * SIZE)
            for (i in outPixels.indices) {
                val b = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                val g = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                val r = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                outPixels[i] = Color.rgb(r, g, b)
            }
            val cartoon = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            cartoon.setPixels(outPixels, 0, SIZE, 0, 0, SIZE, SIZE)

            // devolver al tamano original del bitmap fuente
            val restored = if (w == SIZE && h == SIZE) cartoon
                else Bitmap.createScaledBitmap(cartoon, w, h, true).also { cartoon.recycle() }

            // conservar el alpha original (foto ya recortada por la IA)
            val srcPixels = IntArray(w * h)
            source.getPixels(srcPixels, 0, w, 0, 0, w, h)
            val resPixels = IntArray(w * h)
            restored.getPixels(resPixels, 0, w, 0, 0, w, h)
            for (i in resPixels.indices) {
                val a = srcPixels[i] ushr 24
                if (a != 255) resPixels[i] = (a shl 24) or (resPixels[i] and 0x00FFFFFF)
            }
            restored.setPixels(resPixels, 0, w, 0, 0, w, h)
            restored
        } catch (e: Exception) {
            null
        } finally {
            try { interpreter.close() } catch (_: Exception) {}
        }
    }
}
