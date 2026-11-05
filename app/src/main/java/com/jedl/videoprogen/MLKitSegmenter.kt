package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.nio.FloatBuffer

object MLKitSegmenter {
    fun processImage(context: Context, bitmap: Bitmap, onResult: (Bitmap) -> Unit) {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundConfidenceMask()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        segmenter.process(image).addOnSuccessListener { result ->
            // NOTA DE API REAL (referencia oficial de ML Kit):
            // getForegroundConfidenceMask() devuelve un FloatBuffer con UN float
            // 0.0-1.0 por pixel (mismo tamano que la foto), NO un Bitmap.
            val mask: FloatBuffer? = result.foregroundConfidenceMask
            if (mask != null) {
                val width = bitmap.width
                val height = bitmap.height
                mask.rewind()

                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                for (i in pixels.indices) {
                    // Si la mascara es oscura (fondo), volvemos el pixel transparente
                    if (mask.get(i) < 0.5f) {
                        pixels[i] = Color.TRANSPARENT
                    }
                }
                val resultBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                resultBmp.setPixels(pixels, 0, width, 0, 0, width, height)
                onResult(resultBmp)
            } else {
                // La IA no produjo mascara: mismo fallback que si fallara
                onResult(applyChromaKeyFallback(bitmap))
            }
        }.addOnFailureListener { e ->
            Toast.makeText(context, "IA falló: ${e.message}. Usando recorte por color.", Toast.LENGTH_LONG).show()
            // FALLBACK a Chroma Key si falla ML Kit
            onResult(applyChromaKeyFallback(bitmap))
        }
    }

    private fun applyChromaKeyFallback(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            if (Color.red(c) > 240 && Color.green(c) > 240 && Color.blue(c) > 240) {
                pixels[i] = Color.TRANSPARENT
            }
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
