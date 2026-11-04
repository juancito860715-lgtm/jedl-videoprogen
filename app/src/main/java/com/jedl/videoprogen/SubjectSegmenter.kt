package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ============================================================
// RECORTE CON IA — MediaPipe ImageSegmenter (selfie segmenter)
// ============================================================
// Segmentación semántica on-device con el modelo oficial
// selfie_segmenter.tflite (float16). El modelo NO está bundleado
// en el APK: se descarga UNA sola vez a filesDir (caché local,
// luego funciona offline).
//
// FÓRMULA DE APLICACIÓN (Alpha Blending):
//   alpha_final = confidence_mask[pixel] * 255
//   Si alpha_final < 128  -> el píxel es FONDO -> transparente.
//
// REGLA DE FALLBACK OBLIGATORIA: si el modelo no se puede
// conseguir (sin red la primera vez), la inferencia falla o
// tarda MÁS DE 200 ms, devuelve null y el llamador cae al
// Chroma Key. NUNCA lanza excepción: la app no se cierra.
// ============================================================
object SubjectSegmenter {

    private const val MODEL_URL =
        "https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite"
    private const val MODEL_FILE = "selfie_segmenter.tflite"
    private const val MIN_MODEL_BYTES = 10_000L     // anti-archivo-corrupto
    private const val MAX_IA_MS = 200L              // umbral de latencia

    @Volatile private var segmenter: ImageSegmenter? = null

    // Descarga el modelo (solo la primera vez) y crea el segmentador.
    private fun ensureSegmenter(context: Context): Boolean {
        segmenter?.let { return true }
        return try {
            val file = File(context.filesDir, MODEL_FILE)
            if (!file.exists() || file.length() < MIN_MODEL_BYTES) {
                downloadModel(file)
            }
            segmenter = ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelFilepath(file.absolutePath).build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    // La máscara de categoría da ancho/alto de la grilla;
                    // la de CONFIANZA (float32, 0.0..1.0) da el alpha suave.
                    .setOutputCategoryMask(true)
                    .setOutputConfidenceMasks(true)
                    .build()
            )
            true
        } catch (e: Exception) {
            // Modelo corrupto o red caída: borrar y caer al Chroma Key
            try { File(context.filesDir, MODEL_FILE).delete() } catch (_: Exception) {}
            segmenter = null
            false
        }
    }

    private fun downloadModel(dest: File) {
        val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.inputStream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    // Devuelve la foto con el fondo transparente, o null si la IA
    // no está disponible / falló / tardó más de 200 ms.
    fun cutSubject(context: Context, bitmap: Bitmap): Bitmap? {
        if (!ensureSegmenter(context)) return null
        val s = segmenter ?: return null
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val result = s.segment(BitmapImageBuilder(bitmap).build())
            try {
                val cat = result.categoryMask().get()
                val mw = cat.width
                val mh = cat.height
                val masks = result.confidenceMasks()
                if (masks.isEmpty()) return null
                // Última categoría = PERSONA (selfie_segmenter: 0=fondo, 1=persona)
                val mask = masks[masks.size - 1]
                mask.rewind()
                val w = bitmap.width
                val h = bitmap.height
                val src = IntArray(w * h)
                bitmap.getPixels(src, 0, w, 0, 0, w, h)
                val out = IntArray(w * h)
                for (y in 0 until h) {
                    val my = (y * mh / h).coerceAtMost(mh - 1)
                    val row = my * mw
                    for (x in 0 until w) {
                        val mx = (x * mw / w).coerceAtMost(mw - 1)
                        // alpha_final = confidence * 255; si < 128 es fondo
                        val alpha = (mask.get(row + mx) * 255f).toInt()
                        if (alpha >= 128) out[y * w + x] = src[y * w + x]
                    }
                }
                val elapsed = SystemClock.elapsedRealtime() - t0
                if (elapsed > MAX_IA_MS) null else Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            } finally {
                result.close()
            }
        } catch (e: Exception) {
            null
        }
    }
}
