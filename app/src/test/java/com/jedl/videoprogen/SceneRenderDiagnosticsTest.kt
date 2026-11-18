package com.jedl.videoprogen

import org.junit.Assert.*
import org.junit.Test

class SceneRenderDiagnosticsTest {
    @Test fun completedFrameReportsRenderedAndFailedLayers() {
        SceneRenderDiagnostics.beginFrame()
        SceneRenderDiagnostics.complete("PROJECT_SCENE", 3, 2)
        assertEquals("PROJECT_SCENE", SceneRenderDiagnostics.renderPath)
        assertEquals(3, SceneRenderDiagnostics.layersDrawn)
        assertEquals(2, SceneRenderDiagnostics.layersFailed)
    }

    @Test fun beginningNextFrameClearsPreviousFailureCounts() {
        SceneRenderDiagnostics.complete("PROJECT_SCENE", 1, 4)
        SceneRenderDiagnostics.beginFrame()
        assertEquals("NOT_RENDERED", SceneRenderDiagnostics.renderPath)
        assertEquals(0, SceneRenderDiagnostics.layersDrawn)
        assertEquals(0, SceneRenderDiagnostics.layersFailed)
    }
}
