package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectDocumentValidatorTest {

    private fun validProject() = ProjectRepository.newProject("Test")

    @Test
    fun acceptsDefaultProject() {
        assertNull(ProjectDocumentValidator.validate(validProject()))
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val invalid = validProject().copy(schemaVersion = 999)
        assertNotNull(ProjectDocumentValidator.validate(invalid))
    }

    @Test
    fun rejectsOddCanvasDimensions() {
        val invalid = validProject().copy(width = 721)
        assertNotNull(ProjectDocumentValidator.validate(invalid))
    }

    @Test
    fun rejectsInvalidFrameRate() {
        val invalid = validProject().copy(frameRate = 120)
        assertNotNull(ProjectDocumentValidator.validate(invalid))
    }

    @Test
    fun rejectsEmptyProjectName() {
        val invalid = validProject().copy(name = " ")
        assertNotNull(ProjectDocumentValidator.validate(invalid))
    }

    @Test
    fun rejectsNonFiniteOpacityAndTransform() {
        val scene = SceneDocument(layers = listOf(LayerDocument(opacity = Float.NaN)))
        assertNotNull(ProjectDocumentValidator.validate(validProject().copy(scenes = listOf(scene))))

        val badTransform = Transform2D(x = Float.POSITIVE_INFINITY)
        val sceneWithBadTransform = SceneDocument(layers = listOf(LayerDocument(transform = badTransform)))
        assertNotNull(ProjectDocumentValidator.validate(validProject().copy(scenes = listOf(sceneWithBadTransform))))
    }

    @Test
    fun rejectsTraversalAndAbsoluteAssetPaths() {
        assertFalse(ProjectDocumentValidator.isSafeRelativeAssetPath("../outside.png"))
        assertFalse(ProjectDocumentValidator.isSafeRelativeAssetPath("/data/local/tmp/image.png"))
        assertFalse(ProjectDocumentValidator.isSafeRelativeAssetPath("C:/outside.png"))
        assertFalse(ProjectDocumentValidator.isSafeRelativeAssetPath("images\\..\\outside.png"))
        assertTrue(ProjectDocumentValidator.isSafeRelativeAssetPath("assets/characters/hero.png"))
    }

    @Test
    fun validatesIndependentLayersAndScenes() {
        val scene = SceneDocument(layers = listOf(
            LayerDocument(name = "Background", type = LayerType.BACKGROUND, zIndex = 0),
            LayerDocument(name = "Character", type = LayerType.CHARACTER, zIndex = 1),
            LayerDocument(name = "Title", type = LayerType.TEXT, text = "Hola", zIndex = 2)
        ))
        assertEquals(3, scene.layers.size)
        assertNull(ProjectDocumentValidator.validate(validProject().copy(scenes = listOf(scene))))
    }
}
