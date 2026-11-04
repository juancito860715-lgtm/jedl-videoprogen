package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import java.nio.ByteOrder

// ============================================================
// RECORTE CON IA — MediaPipe ImageSegmenter (selfie multiclass)
// ============================================================
// Segmentación semántica on-device con el modelo
// selfie_multiclass_256x256.tflite (Apache 2.0), que YA VIENE
// DENTRO DEL APK (lo descarga el workflow de CI a assets/).
// Funciona OFFLINE desde el primer uso: no necesita red.
//
// Categorías: 0 = FONDO, 1-5 = partes de la persona
// (pelo, piel del cuerpo, piel de la cara, ropa, otros).
//
// FÓRMULA DE APLICACIÓN (Alpha Blending):
//   conf_persona = 1 - confidence_mask_fondo[pixel]
//   alpha_final  = conf_persona * 255
//   Si alpha_final < 128  -> el píxel es FONDO -> transparente.
//
// API verificada en tasks-vision 0.10.14 (la misma del código
// anterior que ya compilaba en este repo):
//   - BaseOptions.builder().setModelAssetPath(...)
//   - result.confidenceMasks() -> Optional<List<MPImage>>
//   - ByteBufferExtractor.extract(MPImage) -> ByteBuffer
//
// REGLA DE FALLBACK OBLIGATORIA: si la inferencia falla o tarda
// MÁS DE 200 ms, devuelve null y el llamador cae al Chroma Key
// (con Toast). NUNCA lanza excepción: la app no se cierra.
// ============================================================
object SubjectSegmenter {

    private const val MODEL_ASSET = "selfie_multiclass_256x256.tflite"
    private const val MAX_IA_MS = 200L              // umbral de latencia

    @Volatile private var segmenter: ImageSegmenter? = null

    // Crea el segmentador una sola vez. NUNCA lanza excepción.
    private fun ensureSegmenter(context: Context): Boolean {
        segmenter?.let { return true }
        return try {
            segmenter = ImageSegmenter.createFromOptions(
                context,
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
                    )
                    .setRunningMode(RunningMode.IMAGE)
                    // CONFIDENCE_MASK (float32, 0.0-1.0) para el alpha suave
                    .setOutputConfidenceMasks(true)
                    .setOutputCategoryMask(false)
                    .build()
            )
            true
        } catch (e: Exception) {
            segmenter = null
            false
        }
    }

    // Devuelve la foto con el fondo transparente, o null si la IA
    // no está disponible / falló / tardó más de 200 ms.
    fun cutSubject(context: Context, bitmap: Bitmap): Bitmap? {
        if (!ensureSegmenter(context)) return null
        val s = segmenter ?: return null
        val t0 = SystemClock.elapsedRealtime()
        return try {
            val result = s.segment(BitmapImageBuilder(bitmap).build())
            val masksOpt = result.confidenceMasks()   // Optional<List<MPImage>>
            if (!masksOpt.isPresent) return null
            val masks = masksOpt.get()
            if (masks.isEmpty()) return null
            // masks[0] = confianza de FONDO (categoría 0). MPImage.
            val bgImg = masks[0]
            val mw = bgImg.width
            val mh = bgImg.height
            val fb = ByteBufferExtractor.extract(bgImg)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()   // float32, 0.0..1.0
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
                    // alpha_final = (1 - conf_fondo) * 255; si < 128 es fondo
                    val alpha = ((1f - fb.get(row + mx)) * 255f).toInt()
                    if (alpha >= 128) out[y * w + x] = src[y * w + x]
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - t0
            if (elapsed > MAX_IA_MS) null else Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }
}
