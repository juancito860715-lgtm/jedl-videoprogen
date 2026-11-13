package com.jedl.videoprogen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectLayerTimingTest {
    @Test
    fun layerIsVisibleAtStartAndUntilButNotIncludingEnd() {
        val layer = LayerDocument(startMillis = 250L, durationMillis = 1_000L)
        assertFalse(ProjectLayerTiming.isActive(layer, 249L))
        assertTrue(ProjectLayerTiming.isActive(layer, 250L))
        assertTrue(ProjectLayerTiming.isActive(layer, 1_249L))
        assertFalse(ProjectLayerTiming.isActive(layer, 1_250L))
    }

    @Test
    fun hiddenLayerNeverBecomesActive() {
        val layer = LayerDocument(visible = false, startMillis = 0L, durationMillis = 5_000L)
        assertFalse(ProjectLayerTiming.isActive(layer, 0L))
        assertFalse(ProjectLayerTiming.isActive(layer, 2_500L))
    }

    @Test
    fun negativeSceneTimeIsNotActive() {
        val layer = LayerDocument(startMillis = 0L, durationMillis = 5_000L)
        assertFalse(ProjectLayerTiming.isActive(layer, -1L))
    }
}
