package com.jedl.videoprogen

/**
 * Muestrea un clip persistente sin depender de Android Canvas ni de la interfaz.
 *
 * El tiempo recibido es relativo al comienzo de la capa que contiene al personaje.
 * Si el clip está en loop, vuelve al inicio al alcanzar durationMillis; si no,
 * mantiene la última pose. Entre keyframes se usa el mismo smoothstep del motor.
 */
object ProjectAnimationSampler {

    /**
     * Devuelve la pose del primer clip asociado a layerId, o null si la capa
     * todavía no tiene animación propia.
     */
    fun poseForLayer(
        scene: SceneDocument,
        layer: LayerDocument,
        sceneTimeMillis: Long
    ): Pose? {
        val clip = scene.animationClips.firstOrNull { it.targetLayerId == layer.id } ?: return null
        return sample(clip, sceneTimeMillis - layer.startMillis)
    }

    fun sample(clip: AnimationClipDocument, elapsedMillis: Long): Pose {
        require(clip.durationMillis > 0L) { "La duración del clip debe ser positiva" }
        val frames = clip.keyframes
        require(frames.isNotEmpty()) { "El clip no contiene keyframes" }
        // ProjectDocumentValidator comprueba el orden y los límites al guardar/abrir.
        // Aquí solo se hacen comprobaciones O(1): el sampler se ejecuta por cada frame.
        require(frames.first().timeMillis in 0L..clip.durationMillis &&
            frames.last().timeMillis in 0L..clip.durationMillis) {
            "Los keyframes están fuera de la duración del clip"
        }

        val elapsed = elapsedMillis.coerceAtLeast(0L)
        val sampleTime = if (clip.loop) {
            elapsed % clip.durationMillis
        } else {
            elapsed.coerceAtMost(clip.durationMillis)
        }

        val first = frames.first()
        if (sampleTime <= first.timeMillis) return first.toPose()

        val lastIndex = frames.lastIndex
        val last = frames[lastIndex]
        if (sampleTime >= last.timeMillis) return last.toPose()

        // Búsqueda binaria: el coste no crece linealmente con clips largos.
        var low = 0
        var high = lastIndex
        while (low + 1 < high) {
            val middle = (low + high) ushr 1
            if (frames[middle].timeMillis <= sampleTime) low = middle else high = middle
        }

        val start = frames[low]
        val end = frames[high]
        val span = end.timeMillis - start.timeMillis
        val linear = ((sampleTime - start.timeMillis).toDouble() / span.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
        val eased = linear * linear * (3f - 2f * linear)

        val angleNames = LinkedHashSet<String>().apply {
            addAll(start.boneAngles.keys)
            addAll(end.boneAngles.keys)
        }
        val angles = LinkedHashMap<String, Float>(angleNames.size)
        for (name in angleNames) {
            val from = start.boneAngles[name] ?: 0f
            val to = end.boneAngles[name] ?: 0f
            angles[name] = from + (to - from) * eased
        }

        return Pose(
            angles = angles,
            bob = start.rootBob + (end.rootBob - start.rootBob) * eased,
            sway = start.rootSway + (end.rootSway - start.rootSway) * eased
        )
    }

    private fun AnimationKeyframeDocument.toPose() =
        Pose(boneAngles.toMap(), rootBob, rootSway)
}
