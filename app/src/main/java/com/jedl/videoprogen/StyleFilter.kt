package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object StyleFilter {
    fun apply(bitmap: Bitmap, style: String): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(result)
        val paint = Paint()

        when (style) {
            "ANIME" -> {
                val cm = ColorMatrix()
                cm.setSaturation(1.5f)
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            "COMIC" -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                val contrast = 1.5f
                val translate = (-0.5f * contrast + 0.5f) * 255f
                cm.postConcat(ColorMatrix(floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
                )))
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
            "BW" -> {
                val cm = ColorMatrix()
                cm.setSaturation(0f)
                paint.colorFilter = ColorMatrixColorFilter(cm)
            }
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
}
