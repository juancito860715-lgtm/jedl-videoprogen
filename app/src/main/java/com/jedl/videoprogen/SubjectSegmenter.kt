package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter

// Silueta de la persona: MediaPipe ImageSegmenter (selfie_multiclass, on-device, offline).
// Devuelve un bitmap IGUAL a la foto pero con el fondo en transparente.
class SubjectSegmenter(context: Context) {
    private val segmenter: ImageSegmenter = ImageSegmenter.createFromOptions(
        context,
        ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath("selfie_multiclass_256x256.tflite").build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setOutputCategoryMask(true)
            .setOutputConfidenceMasks(false)
            .build()
    )

    fun cutSubject(bitmap: Bitmap): Bitmap? {
        return try {
            val result = segmenter.segment(BitmapImageBuilder(bitmap).build())
            val mask = result.categoryMask().get()
            val maskBuffer = ByteBufferExtractor.extract(mask)
            val mw = mask.width
            val mh = mask.height
            val w = bitmap.width
            val h = bitmap.height
            val src = IntArray(w * h)
            bitmap.getPixels(src, 0, w, 0, 0, w, h)
            val out = IntArray(w * h)
            // Categoria 0 = fondo; 1-5 = partes de la persona (pelo, piel, ropa, otros)
            for (y in 0 until h) {
                val my = (y * mh / h).coerceAtMost(mh - 1)
                val row = my * mw
                for (x in 0 until w) {
                    val mx = (x * mw / w).coerceAtMost(mw - 1)
                    val cat = maskBuffer.get(row + mx).toInt() and 0xff
                    if (cat != 0) out[y * w + x] = src[y * w + x]
                }
            }
            Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            null
        }
    }
}
