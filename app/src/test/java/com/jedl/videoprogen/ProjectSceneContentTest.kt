package com.jedl.videoprogen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSceneContentTest {
    @Test fun backgroundImageCharacterAndTextAreRenderable() {
        val scene = SceneDocument(layers = listOf(
            LayerDocument(type = LayerType.BACKGROUND, assetPath = "assets/bg.png"),
            LayerDocument(type = LayerType.IMAGE, assetPath = "assets/photo.png"),
            LayerDocument(type = LayerType.CHARACTER),
            LayerDocument(type = LayerType.TEXT, text = "Título"),
            LayerDocument(type = LayerType.SUBTITLE, text = "Subtítulo")
        ))
        assertTrue(ProjectSceneContent.hasRenderableVisual(scene))
    }

    @Test fun hiddenOrOutOfSceneLayersDoNotEnableExport() {
        val scene = SceneDocument(durationMillis = 1_000L, layers = listOf(
            LayerDocument(type = LayerType.TEXT, text = "Oculto", visible = false),
            LayerDocument(type = LayerType.IMAGE, assetPath = "assets/late.png", startMillis = 1_000L),
            LayerDocument(type = LayerType.TEXT, text = "")
        ))
        assertFalse(ProjectSceneContent.hasRenderableVisual(scene))
    }

    @Test fun timedTextAndSubtitleTracksCanRenderWithoutLayers() {
        val scene = SceneDocument(timelineTracks = listOf(
            TimelineTrackDocument(
                name = "Subtítulos",
                type = TimelineTrackType.SUBTITLE,
                clips = listOf(TimelineClipDocument(name = "Entrada", text = "Hola", startMillis = 500L))
            )
        ))
        assertTrue(ProjectSceneContent.hasRenderableVisual(scene))
    }

    @Test fun videoAndAudioLayersAreNotAdvertisedAsRenderableYet() {
        val scene = SceneDocument(layers = listOf(
            LayerDocument(type = LayerType.VIDEO, assetPath = "assets/clip.mp4"),
            LayerDocument(type = LayerType.AUDIO, assetPath = "assets/music.wav")
        ))
        assertFalse(ProjectSceneContent.hasRenderableVisual(scene))
    }
}
