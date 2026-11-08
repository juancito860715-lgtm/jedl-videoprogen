package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ============================================================
// FASE B — RECORTE DE LA CARA (v0.6.0)
// ============================================================
// Recorta SOLO LA CARA de la foto (pedido del dueño: el recorte de
// la imagen pasa a ser solo cara, para personalizar el avatar).
//
// PLAN A (vía principal): MediaPipe Face Landmarker (478 puntos,
// face_landmarker.task ~3.7 MB que DESCARGA EL CI a assets, igual
// que el pose_landmarker). Con el recuadro de los puntos construye
// una elipse con ALFA SUAVE (smoothstep del borde): la cara se
// funde con el círculo de piel de la cabeza del avatar.
// API verificada en tasks-vision 0.10.14 con el MISMO patrón del
// VideoMotionCloner ya en el repo: result.faceLandmarks() devuelve
// la lista de caras y cada landmark se lee con .x() / .y().
//
// PLAN B (SIEMPRE disponible, 100% offline): elipse MANUAL — el
// dedo coloca el centro sobre la foto y un botón agranda/achica.
// Si la IA no está (o el modelo no se descargó en el build) la app
// NUNCA se rompe: cae a este plan B automáticamente.
// ============================================================
object FaceCutter {

    private const val MODEL_ASSET = "face_landmarker.task"

    @Volatile private var landmarker: FaceLandmarker? = null

    // Crea el landmarker UNA sola vez. NUNCA lanza excepción: si el
    // modelo no está en assets devuelve null y el caller usa el plan B.
    private fun ensure(context: Context): FaceLandmarker? {
        landmarker?.let { return it }
        return try {
            landmarker = FaceLandmarker.createFromOptions(
                context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumFaces(1)
                    .build()
            )
            landmarker
        } catch (t: Throwable) {
            landmarker = null
            null
        }
    }

    // PLAN A: cara recortada con IA, o null si no hay modelo/cara.
    fun cutFaceAuto(context: Context, bitmap: Bitmap): Bitmap? {
        val fl = ensure(context) ?: return null
        return try {
            val result = fl.detect(BitmapImageBuilder(bitmap).build())
            val face = result.faceLandmarks().firstOrNull() ?: return null
            if (face.isEmpty()) return null
            // recuadro de los 478 puntos (coordenadas normalizadas 0..1)
            var minX = 1f; var minY = 1f; var maxX = 0f; var maxY = 0f
            for (lm in face) {
                val x = lm.x(); val y = lm.y()
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            if (maxX <= minX || maxY <= minY) return null
            // margen del 12-18% para no cortar el pelo ni la barbilla
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val cx = (minX + maxX) * 0.5f * w
            val cy = (minY + maxY) * 0.5f * h
            val rx = (maxX - minX) * 0.5f * w * 1.12f
            val ry = (maxY - minY) * 0.5f * h * 1.18f
            cutEllipse(bitmap, cx, cy, rx, ry)
        } catch (t: Throwable) {
            null
        }
    }

    // PLAN B: elipse manual. cxFr/cyFr = centro en fracciones de la
    // foto (0..1), rFr = radio en fracción del lado MENOR.
    fun cutFaceManual(bitmap: Bitmap, cxFr: Float, cyFr: Float, rFr: Float): Bitmap {
        val r = rFr.coerceIn(0.04f, 0.48f)
        val m = min(bitmap.width.toFloat(), bitmap.height.toFloat())
        return cutEllipse(bitmap, cxFr.coerceIn(0f, 1f) * bitmap.width, cyFr.coerceIn(0f, 1f) * bitmap.height, r * m, r * m * 1.15f)
    }

    // Recorta una elipse con alfa suave (smoothstep en los últimos
    // 18% del radio). Devuelve el bitmap del recuadro con la elipse
    // semitransparente en los bordes (lista para montar en la cabeza).
    private fun cutEllipse(src: Bitmap, cx: Float, cy: Float, rx0: Float, ry0: Float): Bitmap {
        val rx = max(4f, rx0)
        val ry = max(4f, ry0)
        val left = max(0, (cx - rx - 3f).toInt())
        val top = max(0, (cy - ry - 3f).toInt())
        val right = min(src.width, (cx + rx + 3f).toInt())
        val bottom = min(src.height, (cy + ry + 3f).toInt())
        val w = right - left
        val h = bottom - top
        if (w < 4 || h < 4) {
            // degenerado: 1 px transparente (nunca rompe)
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, left, top, w, h)
        val feather = 0.18f
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val dx = (left + x + 0.5f - cx) / rx
                val dy = (top + y + 0.5f - cy) / ry
                val d = sqrt(dx * dx + dy * dy)
                val t = ((d - (1f - feather)) / feather).coerceIn(0f, 1f)
                val a = (t * t * (3f - 2f * t) * 255f).toInt()
                pixels[row + x] = (pixels[row + x] and 0x00FFFFFF) or (a shl 24)
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
