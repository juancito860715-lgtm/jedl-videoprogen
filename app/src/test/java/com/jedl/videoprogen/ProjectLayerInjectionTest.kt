package com.jedl.videoprogen

import org.junit.Assert.*
import org.junit.Test

class ProjectLayerInjectionTest {
    @Test fun actorPhotoIsInsertedBelowCharacter() {
        val scene = SceneDocument()
        val updated = ProjectLayerInjection.injectAsset(scene, "assets/actor.png", LayerType.IMAGE)
        val character = updated.layers.single { it.type == LayerType.CHARACTER }
        val image = updated.layers.single { it.type == LayerType.IMAGE }
        assertTrue(image.zIndex < character.zIndex)
        assertNotNull(character.characterAppearance)
        assertEquals("assets/actor.png", image.assetPath)
    }

    @Test fun backgroundIsInsertedBehindExistingSceneLayers() {
        val character = LayerDocument(type = LayerType.CHARACTER, zIndex = 0)
        val image = LayerDocument(type = LayerType.IMAGE, assetPath = "assets/photo.png", zIndex = 1)
        val updated = ProjectLayerInjection.injectAsset(
            SceneDocument(layers = listOf(character, image)), "assets/bg.png", LayerType.BACKGROUND
        )
        val background = updated.layers.single { it.type == LayerType.BACKGROUND }
        assertTrue(background.zIndex < updated.layers.filter { it.id != background.id }.minOf { it.zIndex })
    }
}
