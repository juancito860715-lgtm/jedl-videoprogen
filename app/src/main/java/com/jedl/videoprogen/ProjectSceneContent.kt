package com.jedl.videoprogen

/**
 * Indica si una escena contiene contenido visual que el renderizador actual
 * puede producir. VIDEO/AUDIO se excluyen hasta que exista decodificación y
 * mezcla reales; una pista importada por sí sola no equivale a vídeo exportable.
 */
object ProjectSceneContent {
    fun hasRenderableVisual(scene: SceneDocument): Boolean =
        scene.layers.any { layer ->
            layer.visible && layer.startMillis < scene.durationMillis &&
                layer.durationMillis > 0L && when (layer.type) {
                    LayerType.BACKGROUND, LayerType.IMAGE -> !layer.assetPath.isNullOrBlank()
                    LayerType.CHARACTER -> true
                    LayerType.TEXT, LayerType.SUBTITLE -> layer.text.isNotBlank()
                    LayerType.VIDEO, LayerType.AUDIO -> false
                }
        } || scene.timelineTracks.any { track ->
            !track.muted && track.type in setOf(TimelineTrackType.TEXT, TimelineTrackType.SUBTITLE) &&
                track.clips.any { clip ->
                    clip.text.isNotBlank() && clip.startMillis < scene.durationMillis &&
                        clip.durationMillis > 0L
                }
        }
}
