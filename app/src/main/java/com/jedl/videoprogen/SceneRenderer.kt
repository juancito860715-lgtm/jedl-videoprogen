package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

// MOTOR ÚNICO de render: la vista previa Y el export usan ESTA misma función.
// v0.4.0: MUÑECO PRE-RIGGEADO con intercambio de textura (cara + torso de la
// foto pegados a los huesos). El recorte en pedazos fue ELIMINADO: el muñeco
// tiene articulaciones diseñadas a la perfección y NUNCA deja huecos.
object SceneRenderer {

    fun renderScene(
        canvas: Canvas, w: Int, h: Int, progress: Float,
        background: Bitmap?, skin: PuppetSkin?, text: String,
        template: String, prevTemplate: String, blend: Float,
        drawSkeleton: Boolean
    ) {
        // 1) FONDO: negro o la imagen elegida desde galería (cover-fit)
        canvas.drawColor(Color.BLACK)
        if (background != null) {
            val bs = maxOf(w.toFloat() / background.width, h.toFloat() / background.height)
            val bdw = background.width * bs
            val bdh = background.height * bs
            val bdx = (w - bdw) / 2f
            val bdy = (h - bdh) / 2f
            canvas.drawBitmap(background, null, RectF(bdx, bdy, bdx + bdw, bdy + bdh), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 2) MUÑECO: mezcla suave entre la plantilla anterior y la nueva
        val s = blend * blend * (3f - 2f * blend)      // smoothstep: transición fluida
        val pose = PuppetEngine.lerpPose(
            PuppetEngine.poseFor(prevTemplate, progress),
            PuppetEngine.poseFor(template, progress),
            s
        )
        val unit = min(w.toFloat(), h.toFloat()) * 0.88f / 0.85f   // píxeles por unidad de muñeco
        val ox = w / 2f + pose.sway * unit
        val oy = h * 0.55f + pose.bob * unit
        val solved = PuppetRig.HUMANOID.solve(pose.angles, 0f, 0f)

        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(unit, unit)
        PuppetEngine.drawPuppet(canvas, solved, skin)
        canvas.restore()

        // 3) ESQUELETO (solo vista previa): huesos del muñeco en dorado
        if (drawSkeleton) {
            val linePaint = Paint().apply {
                color = Color.parseColor("#F5B841"); strokeWidth = 3f; style = Paint.Style.STROKE
            }
            val jointPaint = Paint().apply { color = Color.parseColor("#E87722") }
            for (b in PuppetRig.HUMANOID.bones) {
                val bp = solved[b.name] ?: continue
                val jx = ox + bp.joint.x * unit
                val jy = oy + bp.joint.y * unit
                val tx = ox + bp.tip.x * unit
                val ty = oy + bp.tip.y * unit
                canvas.drawLine(jx, jy, tx, ty, linePaint)
                canvas.drawCircle(jx, jy, 4f, jointPaint)
            }
        }

        // 4) SUBTÍTULO al final, siempre visible encima
        drawText(canvas, w, h, text)
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
        background: Bitmap?, skin: PuppetSkin?, text: String,
        progress: Float, w: Int, h: Int, template: String, drawSkeleton: Boolean
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, background, skin, text, template, template, 1f, drawSkeleton)
        return out
    }
}
