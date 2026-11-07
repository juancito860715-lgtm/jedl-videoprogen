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
// v0.5.1 (FASE A) — BORDE SUAVE (por reporte real del Redmi:
// "el recorte funciona aunque no perfecto"):
// 1. Alfa CONTINUO con smoothstep (antes era binario: píxel sí/no,
//    por eso el borde salía duro y con dientes de sierra).
// 2. CIERRE MORFOLÓGICO: un píxel rodeado por el cuerpo (>=5 de sus
//    8 vecinos opacos) se vuelve cuerpo -> rellena agujeros pequeños
//    de la máscara (bolsillos, entrepierna, pelo suelto).
// 3. FEATHER: box blur separable de radio 2, 2 pasadas, SOLO al
//    canal alfa -> borde difuminado 2-3 px que se funde con la escena.
// Matemática pública (morfología + blur separable), cero librerías.
//
// API verificada en tasks-vision 0.10.14:
//   - BaseOptions.builder().setModelAssetPath(...)
//   - result.confidenceMasks() -> Optional<List<MPImage>>
//   - ByteBufferExtractor.extract(MPImage) -> ByteBuffer
//
// REGLA DE FALLBACK (v0.4.8): la IA es la VÍA PRINCIPAL, no la
// excepción (umbral de seguridad amplio: 5 s).
// ============================================================
object SubjectSegmenter {

    private const val MODEL_ASSET = "selfie_multiclass_256x256.tflite"
    private const val MAX_IA_MS = 5000L   // seguridad anti-cuelgue, NO un límite de calidad

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
    // falló de verdad (excepción o cuelgo > 5 s).
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
            val n = w * h

            // 1) ALFA CONTINUO: conf de persona 0..1 -> smoothstep entre
            //    0.35 y 0.65 (rampa del borde en vez de umbral binario)
            val alpha = IntArray(n)
            for (y in 0 until h) {
                val my = (y * mh / h).coerceAtMost(mh - 1)
                val row = my * mw
                val dy = y * w
                for (x in 0 until w) {
                    val mx = (x * mw / w).coerceAtMost(mw - 1)
                    val conf = 1f - fb.get(row + mx)          // conf de PERSONA
                    val t = ((conf - 0.35f) / 0.30f).coerceIn(0f, 1f)
                    alpha[dy + x] = (t * t * (3f - 2f * t) * 255f).toInt()
                }
            }

            // 2) CIERRE MORFOLÓGICO (1 pasada): píxel interior rodeado
            //    de cuerpo -> cuerpo. Rellena agujeros de la máscara.
            val solid = BooleanArray(n)
            for (i in 0 until n) solid[i] = alpha[i] > 200
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val i = y * w + x
                    if (solid[i]) continue
                    var cnt = 0
                    for (dyy in -1..1) for (dxx in -1..1) {
                        if (dxx == 0 && dyy == 0) continue
                        if (solid[(y + dyy) * w + (x + dxx)]) cnt++
                    }
                    if (cnt >= 5) alpha[i] = 255
                }
            }

            // 3) FEATHER: box blur separable radio 2, 2 pasadas (solo alfa)
            val tmp = IntArray(n)
            for (pass in 0 until 2) {
                for (y in 0 until h) {
                    val row = y * w
                    for (x in 0 until w) {
                        var sum = 0; var cnt = 0
                        for (d in -2..2) {
                            val xx = x + d
                            if (xx in 0 until w) { sum += alpha[row + xx]; cnt++ }
                        }
                        tmp[row + x] = sum / cnt
                    }
                }
                for (x in 0 until w) {
                    for (y in 0 until h) {
                        var sum = 0; var cnt = 0
                        for (d in -2..2) {
                            val yy = y + d
                            if (yy in 0 until h) { sum += tmp[yy * w + x]; cnt++ }
                        }
                        alpha[y * w + x] = sum / cnt
                    }
                }
            }

            // 4) componer: píxel original + alfa suavizado
            val out = IntArray(n)
            for (i in 0 until n) {
                val a = alpha[i]
                if (a <= 2) continue
                out[i] = (src[i] and 0x00FFFFFF) or (a shl 24)
            }
            val elapsed = SystemClock.elapsedRealtime() - t0
            if (elapsed > MAX_IA_MS) null else Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }
}
