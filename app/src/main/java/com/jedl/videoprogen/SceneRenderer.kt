package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.min
import kotlin.math.sin

// MOTOR UNICO de render: la vista previa Y el export usan ESTA misma funcion.
// v0.3.0: animacion CUTOUT por capas (adios al zoom), fondo opcional desde galeria.
object SceneRenderer {

    fun renderScene(
        canvas: Canvas, w: Int, h: Int, progress: Float,
        photo: Bitmap?, subject: Bitmap?, background: Bitmap?,
        pieces: List<Piece>?, jts: List<PointF>, text: String,
        template: String, drawSkeleton: Boolean
    ) {
        // 1) FONDO: negro o la imagen elegida desde galeria (cover-fit)
        canvas.drawColor(Color.BLACK)
        if (background != null) {
            val bs = maxOf(w.toFloat() / background.width, h.toFloat() / background.height)
            val bdw = background.width * bs
            val bdh = background.height * bs
            val bdx = (w - bdw) / 2f
            val bdy = (h - bdh) / 2f
            canvas.drawBitmap(background, null, RectF(bdx, bdy, bdx + bdw, bdy + bdh), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val actor = subject ?: photo
        if (actor == null) { drawText(canvas, w, h, text); return }

        val baseScale = min(w.toFloat() / actor.width, h.toFloat() / actor.height) * 0.85f
        val dx = (w - actor.width * baseScale) / 2f
        val dy = (h - actor.height * baseScale) / 2f
        val angles = CutoutAnimator.anglesFor(template, progress, actor.height.toFloat())

        // 2) PERSONAJE: 5 capas rotando en sus pivotes (orden: piernas, torso, brazos)
        if (pieces != null && pieces.size >= 5) {
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(baseScale, baseScale)
            canvas.translate(0f, angles.bob)
            drawPiece(canvas, pieces[0], angles.legL)
            drawPiece(canvas, pieces[1], angles.legR)
            drawPiece(canvas, pieces[2], 0f)
            drawPiece(canvas, pieces[3], angles.armL)
            drawPiece(canvas, pieces[4], angles.armR)
            canvas.restore()
        } else {
            // Fallback: sin capas -> persona/foto entera con respiracion suave (sin zoom)
            val breathe = 1f + sin(progress * 2f * Math.PI.toFloat()) * 0.008f
            val s2 = baseScale * breathe
            val dw = actor.width * s2
            val dh = actor.height * s2
            val dx2 = (w - dw) / 2f
            val dy2 = (h - dh) / 2f
            canvas.drawBitmap(actor, null, RectF(dx2, dy2, dx2 + dw, dy2 + dh), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 3) ESQUELETO (solo vista previa, para verificar la deteccion)
        if (drawSkeleton && jts.isNotEmpty()) {
            val dw = actor.width * baseScale
            val dh = actor.height * baseScale
            val scaledJoints = jts.map { PointF(dx + (it.x / actor.width) * dw, dy + (it.y / actor.height) * dh) }
            val skeletonPaint = Paint().apply {
                color = Color.parseColor("#E87722"); strokeWidth = 5f; style = Paint.Style.STROKE
            }
            val connections = listOf(Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25), Pair(25, 27), Pair(24, 26), Pair(26, 28))
            for ((i, j) in connections) {
                if (i < scaledJoints.size && j < scaledJoints.size) canvas.drawLine(scaledJoints[i].x, scaledJoints[i].y, scaledJoints[j].x, scaledJoints[j].y, skeletonPaint)
            }
            for (j in scaledJoints) canvas.drawCircle(j.x, j.y, 6f, Paint().apply { color = Color.RED })
        }

        // 4) SUBTITULO al final, siempre visible encima
        drawText(canvas, w, h, text)
    }

    private fun drawPiece(canvas: Canvas, piece: Piece, angleDeg: Float) {
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (angleDeg == 0f) {
            canvas.drawBitmap(piece.bmp, piece.offX, piece.offY, paint)
        } else {
            canvas.save()
            canvas.rotate(angleDeg, piece.pivotX, piece.pivotY)
            canvas.drawBitmap(piece.bmp, piece.offX, piece.offY, paint)
            canvas.restore()
        }
    }

    private fun drawText(canvas: Canvas, w: Int, h: Int, text: String) {
        if (text.isEmpty()) return
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 60f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(8f, 4f, 4f, Color.BLACK)
        }
        canvas.drawText(text, w / 2f, h * 0.9f, textPaint)
    }

    fun renderSceneToBitmap(
        photo: Bitmap?, subject: Bitmap?, background: Bitmap?, pieces: List<Piece>?,
        jts: List<PointF>, txt: String, progress: Float, w: Int, h: Int,
        template: String, drawSkeleton: Boolean
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, photo, subject, background, pieces, jts, txt, template, drawSkeleton)
        return out
    }
}
