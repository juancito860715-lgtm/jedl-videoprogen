package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

// Motor compartido por preview y exportación.
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
        canvas.drawColor(projectScene?.backgroundColor ?: Color.BLACK)
        val projectMode = projectScene != null
        if (!projectMode && background != null) {
            val bs = maxOf(w.toFloat() / background.width, h.toFloat() / background.height)
            val bdw = background.width * bs
            val bdh = background.height * bs
            val bdx = (w - bdw) / 2f
            val bdy = (h - bdh) / 2f
            canvas.drawBitmap(background, null, RectF(bdx, bdy, bdx + bdw, bdy + bdh), Paint(Paint.FILTER_BITMAP_FLAG))
        }

        val sceneTime = projectScene?.let {
            (progress.coerceIn(0f, 1f) * it.durationMillis).toLong()
        } ?: 0L
        val basePose = overridePose ?: run {
            val s = blend * blend * (3f - 2f * blend)
            PuppetEngine.lerpPose(
                PuppetEngine.poseForActive(prevTemplate, progress),
                PuppetEngine.poseForActive(template, progress),
                s
            )
        }
        val drawActor: (LayerDocument?) -> Unit = { layer ->
            val appearance = layer?.characterAppearance
            val layerX = layer?.transform?.x ?: actorX
            val layerY = layer?.transform?.y ?: actorY
            val effectiveActorX = appearance?.actorX ?: layerX
            val effectiveActorY = appearance?.actorY ?: layerY
            val unit = min(w.toFloat(), h.toFloat()) * 0.88f / 0.85f
            val effectiveAvatarMode = if (appearance != null) appearance.enabled else avatarMode
            val effectiveAvatarSpec = appearance?.let {
                AvatarSpec(it.name, it.skin, it.hair, it.hairStyle, it.shirt, it.pants, it.shoes, it.beard)
            } ?: avatarSpec

            // Cada capa de personaje puede tener un clip persistente distinto.
            // Si no tiene clip propio, conserva el movimiento activo del editor.
            val clipPose = if (layer != null && projectScene != null) {
                ProjectAnimationSampler.poseForLayer(
                    projectScene,
                    layer.id,
                    sceneTime - layer.startMillis
                )
            } else {
                null
            }
            val layerPose = clipPose ?: basePose
            val effectiveDirection = appearance?.direction ?: direction
            val avatarPose = if (effectiveAvatarMode && effectiveAvatarSpec != null) {
                PuppetEngine.applyDirection(
                    layerPose,
                    if (clipPose == null) template.builtin ?: "" else "",
                    effectiveDirection
                )
            } else {
                layerPose
            }
            val solved = PuppetRig.HUMANOID.solve(avatarPose.angles, 0f, 0f)

            canvas.save()
            if (layer == null) {
                canvas.translate(w * effectiveActorX, h * effectiveActorY)
            } else {
                canvas.translate(w * (effectiveActorX - layerX), h * (effectiveActorY - layerY))
            }
            canvas.translate(avatarPose.sway * unit, avatarPose.bob * unit)
            canvas.scale(unit, unit)

            if (effectiveAvatarMode && effectiveAvatarSpec != null) {
                if (effectiveDirection == "izq") canvas.scale(-1f, 1f)
                AvatarRenderer.draw(canvas, solved, effectiveAvatarSpec, avatarFace, effectiveDirection)
            } else {
                PuppetEngine.drawPuppet(canvas, solved, mesh)
            }

            if (drawSkeleton && layer == null) {
                val linePaint = Paint().apply {
                    color = Color.parseColor("#F5B841"); strokeWidth = 3f / unit; style = Paint.Style.STROKE
                }
                val jointPaint = Paint().apply { color = Color.parseColor("#E87722") }
                for (bone in PuppetRig.HUMANOID.bones) {
                    val bonePose = solved[bone.name] ?: continue
                    canvas.drawLine(bonePose.joint.x, bonePose.joint.y, bonePose.tip.x, bonePose.tip.y, linePaint)
                    canvas.drawCircle(bonePose.joint.x, bonePose.joint.y, 0.004f, jointPaint)
                }
            }
            canvas.restore()
        }
        if (!projectMode) drawActor(null)

        if (!projectMode) {
            drawText(canvas, w, h, text, textX, textY, textSize)
        } else {
            val scene = requireNotNull(projectScene)
            SceneLayerRenderer.drawLayers(
                canvas, w, h, scene.layers,
                assetResolver ?: { null },
                sceneTime,
                characterRenderer = drawActor
            )
            val timedText = scene.timelineTracks
                .filter { !it.muted && it.type in setOf(TimelineTrackType.TEXT, TimelineTrackType.SUBTITLE) }
                .flatMap { it.clips }
                .filter { sceneTime >= it.startMillis && sceneTime - it.startMillis < it.durationMillis && it.text.isNotBlank() }
            if (timedText.isNotEmpty()) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    this.textSize = (h * 0.035f).coerceIn(24f, 64f)
                    textAlign = Paint.Align.CENTER
                    setShadowLayer(8f, 2f, 3f, Color.BLACK)
                }
                timedText.forEachIndexed { index, clip ->
                    canvas.drawText(clip.text.take(160), w * 0.5f, h * (0.88f - index * 0.055f), paint)
                }
            }
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
            setShadowLayer(10f, 5f, 5f, Color.BLACK)
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
        projectScene: SceneDocument? = null,
        assetResolver: ((String) -> Bitmap?)? = null
    ): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(
            Canvas(out), w, h, progress, background, mesh, text, template, template, 1f,
            drawSkeleton, overridePose, textX, textY, textSize, actorX, actorY,
            avatarMode, avatarSpec, avatarFace, direction, projectScene, assetResolver
        )
        return out
    }
}
