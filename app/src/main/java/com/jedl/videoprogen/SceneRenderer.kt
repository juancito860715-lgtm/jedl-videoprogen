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
        val characterLayer = projectScene?.layers
            ?.filter { it.type == LayerType.CHARACTER && ProjectLayerTiming.isActive(it, sceneTime) }
            ?.maxByOrNull { it.zIndex }
        val shouldDrawActor = !projectMode || characterLayer != null
        if (shouldDrawActor) {
            val pose = overridePose ?: run {
                val s = blend * blend * (3f - 2f * blend)
                PuppetEngine.lerpPose(
                    PuppetEngine.poseForActive(prevTemplate, progress),
                    PuppetEngine.poseForActive(template, progress),
                    s
                )
            }
            val appearance = characterLayer?.characterAppearance
            val effectiveActorX = appearance?.actorX ?: characterLayer?.transform?.x ?: actorX
            val effectiveActorY = appearance?.actorY ?: characterLayer?.transform?.y ?: actorY
            val unit = min(w.toFloat(), h.toFloat()) * 0.88f / 0.85f
            val ox = w * effectiveActorX + pose.sway * unit
            val oy = h * effectiveActorY + pose.bob * unit
            val effectiveAvatarMode = if (appearance != null) appearance.enabled else avatarMode
            val solved = PuppetRig.HUMANOID.solve(pose.angles, 0f, 0f)

            if (effectiveAvatarMode && avatarSpec != null) {
                val effectiveDirection = appearance?.direction ?: direction
                val poseDir = PuppetEngine.applyDirection(pose, template.builtin ?: "", effectiveDirection)
                val solvedAvatar = PuppetRig.HUMANOID.solve(poseDir.angles, 0f, 0f)
                canvas.save()
                canvas.translate(ox, oy)
                canvas.scale(unit, unit)
                if (effectiveDirection == "izq") canvas.scale(-1f, 1f)
                AvatarRenderer.draw(canvas, solvedAvatar, avatarSpec, avatarFace, effectiveDirection)
                canvas.restore()
            } else {
                canvas.save()
                canvas.translate(ox, oy)
                canvas.scale(unit, unit)
                PuppetEngine.drawPuppet(canvas, solved, mesh)
                canvas.restore()
            }

            if (drawSkeleton) {
                val linePaint = Paint().apply {
                    color = Color.parseColor("#F5B841"); strokeWidth = 3f; style = Paint.Style.STROKE
                }
                val jointPaint = Paint().apply { color = Color.parseColor("#E87722") }
                for (bone in PuppetRig.HUMANOID.bones) {
                    val bonePose = solved[bone.name] ?: continue
                    val jx = ox + bonePose.joint.x * unit
                    val jy = oy + bonePose.joint.y * unit
                    val tx = ox + bonePose.tip.x * unit
                    val ty = oy + bonePose.tip.y * unit
                    canvas.drawLine(jx, jy, tx, ty, linePaint)
                    canvas.drawCircle(jx, jy, 4f, jointPaint)
                }
            }
        }

        if (!projectMode) {
            drawText(canvas, w, h, text, textX, textY, textSize)
        } else {
            val scene = requireNotNull(projectScene)
            SceneLayerRenderer.drawLayers(
                canvas, w, h, scene.layers,
                assetResolver ?: { null },
                sceneTime
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
