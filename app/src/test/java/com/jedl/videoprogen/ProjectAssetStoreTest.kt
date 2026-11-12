package com.jedl.videoprogen

import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectAssetStoreTest {
    @Rule @JvmField
    val temporaryFolder = TemporaryFolder()

    @Test
    fun importsAndReopensBytesUsingProjectRelativePath() {
        val store = ProjectAssetStore.forTests(temporaryFolder.root)
        val projectId = ProjectRepository.newProject("Assets").id
        val original = byteArrayOf(0, 1, 2, 3, 10, 13, -1)
        val imported = store.importAsset(projectId, ByteArrayInputStream(original), "fondo.PNG")

        assertEquals(original.size.toLong(), imported.byteCount)
        assertEquals(true, ProjectDocumentValidator.isSafeRelativeAssetPath(imported.relativePath))
        store.openAsset(projectId, imported.relativePath).use { restored ->
            assertArrayEquals(original, restored.readBytes())
        }
    }

    @Test
    fun rejectsTraversalAndOversizedImports() {
        val store = ProjectAssetStore.forTests(temporaryFolder.root)
        val projectId = ProjectRepository.newProject("Assets").id
        try {
            store.openAsset(projectId, "../outside")
            fail("Expected unsafe path to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: no path traversal outside the project directory.
        }

        try {
            store.importAsset(projectId, ByteArrayInputStream(ByteArray(10)), "large.bin", maxBytes = 5)
            fail("Expected oversized asset to be rejected")
        } catch (_: IOException) {
            // Expected: bounded copy prevents unbounded storage use.
        }
    }
}
