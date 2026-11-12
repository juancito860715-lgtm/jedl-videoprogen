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
                        LayerDocument(id = "00000000-0000-0000-0000-000000000001", name = "Personaje", type = LayerType.CHARACTER, zIndex = 1),
                        LayerDocument(name = "Título", type = LayerType.TEXT, text = "Hola JEDL", zIndex = 2)
                    ),
                    timelineTracks = listOf(
                        TimelineTrackDocument(
                            name = "Música",
                            type = TimelineTrackType.MUSIC,
                            clips = listOf(TimelineClipDocument(
                                name = "Base musical",
                                assetPath = "assets/audio/music.wav",
                                startMillis = 500L,
                                durationMillis = 2_000L,
                                sourceStartMillis = 100L,
                                volume = 0.75f
                            ))
                        ),
                        TimelineTrackDocument(
                            name = "Subtítulos",
                            type = TimelineTrackType.SUBTITLE,
                            clips = listOf(TimelineClipDocument(
                                name = "Entrada",
                                startMillis = 250L,
                                durationMillis = 1_000L,
                                text = "Hola mundo"
                            ))
                        )
                    ),
                    animationClips = listOf(
                        AnimationClipDocument(
                            name = "Saludo",
                            targetLayerId = "00000000-0000-0000-0000-000000000001",
                            durationMillis = 1000L,
                            keyframes = listOf(
                                AnimationKeyframeDocument(0L, mapOf("armUR" to -120f)),
                                AnimationKeyframeDocument(1000L, mapOf("armUR" to -90f), rootBob = 0.01f)
                            )
                        )
                    )
                )
            )
        )

        val saved = repository.save(original)
        val loaded = repository.load(saved.id)
        assertEquals(saved.id, loaded.id)
        assertEquals("Corto de prueba", loaded.name)
        assertEquals("Hola JEDL", loaded.scenes.first().layers.first { it.type == LayerType.TEXT }.text)
        val restoredClip = loaded.scenes.first().animationClips.single()
        assertEquals("Saludo", restoredClip.name)
        assertEquals("00000000-0000-0000-0000-000000000001", restoredClip.targetLayerId)
        assertEquals(-90f, restoredClip.keyframes.last().boneAngles["armUR"]!!)
        val restoredMusic = loaded.scenes.first().timelineTracks.first { it.type == TimelineTrackType.MUSIC }.clips.single()
        assertEquals("assets/audio/music.wav", restoredMusic.assetPath)
        assertEquals(500L, restoredMusic.startMillis)
        assertEquals(0.75f, restoredMusic.volume)
        val restoredSubtitle = loaded.scenes.first().timelineTracks.first { it.type == TimelineTrackType.SUBTITLE }.clips.single()
        assertEquals("Hola mundo", restoredSubtitle.text)
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
