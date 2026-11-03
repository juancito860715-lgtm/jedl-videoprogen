package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sin

// MOTOR UNICO de render: la vista previa Y el export usan ESTA misma funcion
// (movida aqui desde MainActivity para que el servicio de export tambien la use)
object SceneRenderer {

    fun renderScene(
        canvas: Canvas, w: Int, h: Int, progress: Float,
        bmp: Bitmap?, jts: List<PointF>, text: String, drawSkeleton: Boolean
    ) {
        canvas.drawColor(Color.BLACK)
        bmp?.let { b ->
            val bounce = 1.0f + (sin(progress * Math.PI * 6).toFloat() * 0.05f)
            val baseScale = min(w.toFloat() / b.width, h.toFloat() / b.height) * 0.85f
            val scale = baseScale * bounce
            val dw = b.width * scale; val dh = b.height * scale
            val dx = (w - dw) / 2f; val dy = (h - dh) / 2f
            canvas.drawBitmap(b, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))

            // Dibujar Texto
            if (text.isNotEmpty()) {
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 60f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    setShadowLayer(8f, 4f, 4f, Color.BLACK)
                }
                canvas.drawText(text, w / 2f, h * 0.9f, textPaint)
            }

            // Arreglo 1: Solo dibujar esqueleto si drawSkeleton es true
            if (drawSkeleton) {
                val scaledJoints = jts.map { PointF(dx + (it.x / b.width) * dw, dy + (it.y / b.height) * dh) }
                val skeletonPaint = Paint().apply { color = Color.parseColor("#E87722"); strokeWidth = 5f; style = Paint.Style.STROKE }
                val connections = listOf(Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25), Pair(25, 27), Pair(24, 26), Pair(26, 28))

                for ((i, j) in connections) {
                    if (i < scaledJoints.size && j < scaledJoints.size) canvas.drawLine(scaledJoints[i].x, scaledJoints[i].y, scaledJoints[j].x, scaledJoints[j].y, skeletonPaint)
                }
                for (j in scaledJoints) canvas.drawCircle(j.x, j.y, 6f, Paint().apply { color = Color.RED })
            }
        }
    }

    fun renderSceneToBitmap(bmp: Bitmap, jts: List<PointF>, txt: String, progress: Float, w: Int, h: Int, drawSkeleton: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, bmp, jts, txt, drawSkeleton)
        return out
    }
}
