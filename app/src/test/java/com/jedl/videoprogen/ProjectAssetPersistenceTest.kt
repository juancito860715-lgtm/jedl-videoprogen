package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ProjectAssetPersistenceTest {
    @Test fun photoLayerAndCharacterFaceSurviveProjectRoundTrip() {
        val temp = Files.createTempDirectory("jedl-project-test").toFile()
        try {
            val repository = ProjectRepository.forTests(temp)
            val project = ProjectRepository.newProject("Persistencia")
            val facePath = "assets/face-test.png"
            val photoPath = "assets/photo-test.png"
            val character = LayerDocument(
                name = "Avatar",
                type = LayerType.CHARACTER,
                characterAppearance = CharacterAppearanceDocument(
                    name = "Ritmo", skin = 1, hair = 2, hairStyle = 2,
                    shirt = 3, pants = 1, shoes = 2, beard = false,
                    faceAssetPath = facePath
                )
            )
            val photo = LayerDocument(
                name = "Foto principal", type = LayerType.IMAGE,
                assetPath = photoPath, zIndex = 1
            )
            val scene = project.scenes.first().copy(layers = listOf(character, photo))
            val saved = repository.save(project.copy(scenes = listOf(scene)))
            val loaded = repository.load(saved.id)
            val loadedScene = loaded.scenes.first()
            assertEquals(photoPath, loadedScene.layers.first { it.type == LayerType.IMAGE }.assetPath)
            assertEquals(facePath, loadedScene.layers.first { it.type == LayerType.CHARACTER }.characterAppearance?.faceAssetPath)
            assertNull(ProjectDocumentValidator.validate(loaded))
            assertTrue(loadedScene.layers.any { it.type == LayerType.IMAGE })
            assertNotNull(loadedScene.layers.first { it.type == LayerType.CHARACTER }.characterAppearance)
        } finally {
            temp.deleteRecursively()
        }
    }
}
