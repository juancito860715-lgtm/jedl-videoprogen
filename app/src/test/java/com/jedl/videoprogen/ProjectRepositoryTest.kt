package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectRepositoryTest {
    @Rule @JvmField
    val temporaryFolder = TemporaryFolder()

    private fun repository() = ProjectRepository.forTests(temporaryFolder.root)

    @Test
    fun savesLoadsListsAndDeletesProject() {
        val repository = repository()
        val original = ProjectRepository.newProject("Corto de prueba").copy(
            scenes = listOf(
                SceneDocument(
                    name = "Escena principal",
                    layers = listOf(
                        LayerDocument(name = "Título", type = LayerType.TEXT, text = "Hola JEDL", zIndex = 2)
                    )
                )
            )
        )

        val saved = repository.save(original)
        val loaded = repository.load(saved.id)
        assertEquals(saved.id, loaded.id)
        assertEquals("Corto de prueba", loaded.name)
        assertEquals("Hola JEDL", loaded.scenes.first().layers.first().text)
        assertEquals(1, repository.list().size)
        assertTrue(repository.delete(saved.id))
        assertFalse(repository.delete(saved.id))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun rejectsTraversalProjectIdentifiers() {
        val repository = repository()
        try {
            repository.load("../outside")
            throw AssertionError("Expected invalid project ID to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: identifier validation prevents escaping the project root.
        }
    }

    @Test
    fun doesNotListCorruptedProjectDocuments() {
        val repository = repository()
        val project = repository.save(ProjectRepository.newProject("Corruptible"))
        val file = java.io.File(temporaryFolder.root, "JEDLStudio/projects/${project.id}/project.json")
        file.writeText("{not-json")
        assertTrue(repository.list().isEmpty())
    }
}
