package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Color

// ============================================================
// RECORTE POR COLOR (CHROMA KEY) — FALLBACK DUAL
// ============================================================
// Técnica: filtrado de píxeles por umbral de color. La más rápida
// y con menor consumo de RAM. FÓRMULA EXACTA:
//   if (R > 240 && G > 240 && B > 240) pixel = TRANSPARENT
// getPixels/setPixels en bloque: ~100x más rápido que
// getPixel/setPixel en un bucle. Corre en hilo secundario.
// ============================================================
object ChromaKey {

    fun applyChromaKey(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            if (r > 240 && g > 240 && b > 240) pixels[i] = Color.TRANSPARENT
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
