package com.jedl.videoprogen

/** Reglas puras de visibilidad temporal para capas del lienzo. */
object ProjectLayerTiming {
    fun isActive(layer: LayerDocument, sceneTimeMillis: Long): Boolean =
        layer.visible &&
            sceneTimeMillis >= layer.startMillis &&
            sceneTimeMillis - layer.startMillis < layer.durationMillis
}
