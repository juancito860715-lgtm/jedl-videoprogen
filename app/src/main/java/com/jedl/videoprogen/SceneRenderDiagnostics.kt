package com.jedl.videoprogen

data class SceneLayerRenderStats(val drawn: Int, val failed: Int)

object SceneRenderDiagnostics {
    @Volatile var renderPath: String = "NOT_RENDERED"
        private set
    @Volatile var layersDrawn: Int = 0
        private set
    @Volatile var layersFailed: Int = 0
        private set

    fun beginFrame() {
        renderPath = "NOT_RENDERED"
        layersDrawn = 0
        layersFailed = 0
    }

    fun complete(path: String, drawn: Int, failed: Int) {
        renderPath = path
        layersDrawn = drawn.coerceAtLeast(0)
        layersFailed = failed.coerceAtLeast(0)
    }
}
