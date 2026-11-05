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
// Modelo: whitebox_cartoon_gan_dr.tflite (2.57 MB, dynamic range
// quantization, entrada/salida float32 de forma FIJA leida del
// propio modelo en runtime: [1,512,512,3]). Lo descarga el CI
// desde Kaggle Models (spsayakpaul/cartoongan, variacion "dr",
// publicada por el autor del modelo) y lo empaqueta dentro del
// APK: 100% offline en el Redmi.
// Licencia Apache 2.0 (tutorial oficial de TensorFlow; pesos de
// los autores del paper, CVPR 2020) -> APTO PARA MONETIZAR.
// Fuentes verificadas:
//   blog.tensorflow.org/2020/09/how-to-create-cartoonizer-with-tf-lite.html
//   kaggle.com/models/spsayakpaul/cartoongan (512x512, BGR)
//
// FORMULAS EXACTAS (blog oficial + metadata del modelo):
//   PRE:  float = pixel/127.5 - 1   (rango [-1, 1], canales BGR:
//         el modelo se entreno con OpenCV, que usa BGR)
//   POST: pixel = (float + 1) * 127.5   (clip 0..255, BGR->RGB)
//   ALPHA: la transparencia del recorte se conserva: el alpha de
//         cada pixel del resultado = alpha del pixel original.
// ============================================================
object Cartoonizer {

    private const val MODEL_ASSET = "whitebox_cartoon_gan_dr.tflite"

    fun apply(context: Context, source: Bitmap): Bitmap? {
        // Cargar el modelo mapeado en memoria (sin copiarlo a la RAM)
        val interpreter = try {
            val afd = context.assets.openFd(MODEL_ASSET)
            val channel = afd.createInputStream().channel
            val bb = channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
            Interpreter(bb, Interpreter.Options().apply { setNumThreads(4) })
        } catch (e: Exception) { return null }

        return try {
            // FORMA REAL leida del tensor de entrada (512 en el modelo dr):
            // el codigo se adapta solo si el modelo cambia de resolucion
            val shape = interpreter.getInputTensor(0).shape()
            if (shape.size != 4 || shape[3] != 3 || shape[1] != shape[2]) return null
            val size = shape[1]
            val w = source.width
            val h = source.height

            // --- PRE: estirar a size x size y aplanar sobre blanco
            //     (la IA espera una imagen RGB opaca)
            val scaled = Bitmap.createScaledBitmap(source, size, size, true)
            val base = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cnv = Canvas(base)
            cnv.drawColor(Color.WHITE)
            cnv.drawBitmap(scaled, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            if (scaled !== source) scaled.recycle()

            val pixels = IntArray(size * size)
            base.getPixels(pixels, 0, size, 0, 0, size, size)
            base.recycle()

            // [1,size,size,3] float32: por cada pixel B,G,R normalizados
            val input = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
            for (c in pixels) {
                input.putFloat((Color.blue(c) / 127.5f) - 1f)
                input.putFloat((Color.green(c) / 127.5f) - 1f)
                input.putFloat((Color.red(c) / 127.5f) - 1f)
            }
            input.rewind()

            val output = ByteBuffer.allocateDirect(size * size * 3 * 4).order(ByteOrder.nativeOrder())
            interpreter.run(input, output)
            output.rewind()

            // --- POST: BGR -> RGB, [-1,1] -> 0..255
            val outPixels = IntArray(size * size)
            for (i in outPixels.indices) {
                val b = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                val g = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                val r = ((output.float * 127.5f) + 127.5f).toInt().coerceIn(0, 255)
                outPixels[i] = Color.rgb(r, g, b)
            }
            val cartoon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            cartoon.setPixels(outPixels, 0, size, 0, 0, size, size)

            // devolver al tamano original del bitmap fuente
            val restored = if (w == size && h == size) cartoon
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
