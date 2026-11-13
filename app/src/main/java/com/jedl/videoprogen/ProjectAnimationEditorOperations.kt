package com.jedl.videoprogen

/** Operaciones puras del editor para que la edición de poses sea verificable sin Android UI. */
object ProjectAnimationEditorOperations {
    fun upsertPose(
        clip: AnimationClipDocument,
        selectedTimeMillis: Long,
        newTimeMillis: Long,
        boneName: String,
        angleDegrees: Float
    ): AnimationClipDocument {
        require(newTimeMillis in 0L..clip.durationMillis) { "El tiempo está fuera de la duración del clip" }
        require(boneName.isNotBlank() && boneName.length <= 64) { "Articulación no válida" }
        require(angleDegrees.isFinite() && angleDegrees in -360f..360f) { "Ángulo no válido" }
        require(clip.keyframes.none { it.timeMillis == newTimeMillis && it.timeMillis != selectedTimeMillis }) {
            "Ya existe una pose en ese instante"
        }
        val selected = clip.keyframes.firstOrNull { it.timeMillis == selectedTimeMillis }
        val base = selected?.boneAngles
            ?: clip.keyframes.lastOrNull { it.timeMillis <= newTimeMillis }?.boneAngles.orEmpty()
        val updated = AnimationKeyframeDocument(
            timeMillis = newTimeMillis,
            boneAngles = base + (boneName to angleDegrees),
            rootBob = selected?.rootBob ?: 0f,
            rootSway = selected?.rootSway ?: 0f
        )
        return clip.copy(
            keyframes = (clip.keyframes.filterNot { it.timeMillis == selectedTimeMillis } + updated)
                .sortedBy { it.timeMillis }
        )
    }

    fun deletePose(clip: AnimationClipDocument, timeMillis: Long): AnimationClipDocument {
        require(clip.keyframes.size > 1) { "El clip debe conservar al menos una pose" }
        require(clip.keyframes.any { it.timeMillis == timeMillis }) { "La pose no existe" }
        return clip.copy(keyframes = clip.keyframes.filterNot { it.timeMillis == timeMillis })
    }
}
