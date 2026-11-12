package com.jedl.videoprogen

/**
 * Adaptador entre el motor de animación existente y el documento persistente.
 * Mantiene el motor (Pose/MotionTemplate) desacoplado del formato JSON.
 */
object ProjectAnimationMapper {
    fun fromMotionTemplate(
        template: MotionTemplate,
        targetLayerId: String,
        durationMillis: Long,
        loop: Boolean = template.loop
    ): AnimationClipDocument {
        require(durationMillis > 0L) { "La duración debe ser positiva" }
        require(template.frames.isNotEmpty()) { "La plantilla no contiene keyframes" }
        val ordered = template.frames.sortedBy { it.t }
        return AnimationClipDocument(
            name = template.name.take(120).ifBlank { "Animación" },
            targetLayerId = targetLayerId,
            durationMillis = durationMillis,
            loop = loop,
            keyframes = ordered.map { frame ->
                require(frame.t.isFinite() && frame.t in 0f..1f) { "Tiempo de keyframe fuera de rango" }
                require(frame.pose.angles.values.all { it.isFinite() }) { "La pose contiene ángulos no válidos" }
                AnimationKeyframeDocument(
                    timeMillis = (frame.t * durationMillis).toLong(),
                    boneAngles = frame.pose.angles.toMap(),
                    rootBob = frame.pose.bob,
                    rootSway = frame.pose.sway
                )
            }.distinctBy { it.timeMillis }
        )
    }

    fun toMotionTemplate(clip: AnimationClipDocument): MotionTemplate {
        require(clip.durationMillis > 0L && clip.keyframes.isNotEmpty()) { "Clip sin duración o keyframes" }
        val frames = clip.keyframes.sortedBy { it.timeMillis }.map { keyframe ->
            PoseFrame(
                (keyframe.timeMillis.toFloat() / clip.durationMillis).coerceIn(0f, 1f),
                Pose(keyframe.boneAngles.toMap(), keyframe.rootBob, keyframe.rootSway)
            )
        }
        return MotionTemplate(clip.name, frames, clip.loop)
    }
}
