package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

// MOTOR ÚNICO de render: la vista previa Y el export usan ESTA misma función.
// v0.5.0: plantillas ACTIVAS (builtin, mocap de video o poses del usuario) y
// OVERRIDE de pose (modo Posar con IK): el editor congela la pose que el
// usuario está arrastrando y este motor la pinta exactamente igual en
// pantalla y en el MP4 exportado.
//
// v0.6.0 (FASE B): MODO PERSONAJE. Si avatarMode está activo se dibuja el
// AVATAR POR PIEZAS (AvatarRenderer) anclado al MISMO rig en vez de la foto
// deformada: plantillas, IK, editor de poses y MOCAP DE VIDEO lo animan
// igual, pero SIN distorsión (no hay píxeles arrastrados entre huesos).
// La DIRECCIÓN ("izq") aplica el ESPEJO alrededor del origen del actor y
// "espalda" oculta la cara (AvatarRenderer).
object SceneRenderer {

    fun renderScene(
        canvas: Canvas, w: Int, h: Int, progress: Float,
        background: Bitmap?, mesh: SkinnedMesh?, text: String,
        template: ActiveTemplate, prevTemplate: ActiveTemplate, blend: Float,
        drawSkeleton: Boolean,
        overridePose: Pose? = null,
        textX: Float = 0.5f, textY: Float = 0.9f, textSize: Float = 60f,
        actorX: Float = 0.5f, actorY: Float = 0.55f,
        avatarMode: Boolean = false, avatarSpec: AvatarSpec? = null,
        avatarFace: Bitmap? = null, direction: String = "der",
        projectScene: SceneDocument? = null,
        assetResolver: ((String) -> Bitmap?)? = null
    ) {
        // 1) FONDO: negro o la imagen elegida desde galería (cover-fit)
        canvas.drawColor(projectScene?.backgroundColor ?: Color.BLACK)
        if (background != null) {
            val bs = maxOf(w.toFloat() / background.width, h.toFloat() / background.height)
            val bdw = background.width * bs
            val bdh = background.height * bs
            val bdx = (w - bdw) / 2f
            val bdy = (h - bdh) / 2f
            canvas.drawBitmap(background, null, RectF(bdx, bdy, bdx + bdw, bdy + bdh), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 2) MUÑECO: pose de la plantilla activa. Si el editor de poses
        //    (IK) está congelando una pose, esa manda SIEMPRE.
        val pose = overridePose ?: run {
            val s = blend * blend * (3f - 2f * blend)      // smoothstep: transición fluida
            PuppetEngine.lerpPose(
                PuppetEngine.poseForActive(prevTemplate, progress),
                PuppetEngine.poseForActive(template, progress),
                s
            )
        }
        val unit = min(w.toFloat(), h.toFloat()) * 0.88f / 0.85f   // píxeles por unidad de muñeco
        // v0.5.1 (FASE A): el actor se COLOCA donde el usuario arrastró
        // (actorX/actorY en proporción del lienzo). Preview y export usan
        // esta MISMA posición: el MP4 queda idéntico a la pantalla.
        val ox = w * actorX + pose.sway * unit
        val oy = h * actorY + pose.bob * unit
        val solved = PuppetRig.HUMANOID.solve(pose.angles, 0f, 0f)

        if (avatarMode && avatarSpec != null) {
            // FASE B: personaje por piezas + dirección (vista) sobre la pose
            val poseDir = PuppetEngine.applyDirection(pose, template.builtin ?: "", direction)
            val solvedAv = PuppetRig.HUMANOID.solve(poseDir.angles, 0f, 0f)
            canvas.save()
            canvas.translate(ox, oy)
            canvas.scale(unit, unit)
            if (direction == "izq") canvas.scale(-1f, 1f)   // perfil izquierdo = espejo
            AvatarRenderer.draw(canvas, solvedAv, avatarSpec, avatarFace, direction)
            canvas.restore()
        } else {
            canvas.save()
            canvas.translate(ox, oy)
            canvas.scale(unit, unit)
            PuppetEngine.drawPuppet(canvas, solved, mesh)
            canvas.restore()
        }

        // 3) ESQUELETO (solo depuración, desactivado por defecto)
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

        // 4) Texto heredado del editor anterior.
        drawText(canvas, w, h, text, textX, textY, textSize)
        if (projectScene != null && assetResolver != null) {
            SceneLayerRenderer.drawLayers(canvas, w, h, projectScene.layers, assetResolver)
        }
    }

    private fun drawText(
        canvas: Canvas, w: Int, h: Int, text: String,
        textX: Float, textY: Float, textSize: Float
    ) {
        if (text.isEmpty()) return
        val textPaint = Paint().apply {
            color = Color.WHITE
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            setShadowLayer(10f, 5f, 5f, Color.BLACK)   // legibilidad sobre cualquier fondo
        }
        canvas.drawText(text, w * textX, h * textY, textPaint)
    }

    fun renderSceneToBitmap(
        background: Bitmap?, mesh: SkinnedMesh?, text: String,
        progress: Float, w: Int, h: Int, template: ActiveTemplate, drawSkeleton: Boolean,
        overridePose: Pose? = null,
        textX: Float = 0.5f, textY: Float = 0.9f, textSize: Float = 60f,
        actorX: Float = 0.5f, actorY: Float = 0.55f,
        avatarMode: Boolean = false, avatarSpec: AvatarSpec? = null,
        avatarFace: Bitmap? = null, direction: String = "der",
        projectScene: SceneDocument? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(
            Canvas(out), w, h, progress, background, mesh, text, template, template, 1f,
            drawSkeleton, overridePose, textX, textY, textSize, actorX, actorY,
            avatarMode, avatarSpec, avatarFace, direction, projectScene, null
        )
        return out
    }
}
