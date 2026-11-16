package com.jedl.videoprogen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperDiagnosticsTest {
    @Test
    fun serializesProjectPhotoFaceAvatarAndLayers() {
        val report = DeveloperDiagnosticsSnapshot(
            activeProjectPresent = true,
            projectId = "project-1",
            projectName = "Demo",
            activeSceneId = "scene-1",
            photoBitmapLoaded = true,
            photoBitmapWidth = 640,
            photoBitmapHeight = 480,
            photoBitmapRecycled = false,
            photoLayerPresent = true,
            photoLayerZIndex = 4,
            photoAssetPath = "images/photo.png",
            faceCutterExecuted = true,
            faceBitmapValid = true,
            faceBitmapWidth = 128,
            faceBitmapHeight = 160,
            facePassedToAvatarRenderer = true,
            pieceLibraryInitialized = true,
            pieceManifestFound = false,
            pngPiecesAvailable = false,
            avatarRenderPath = "VECTOR_FALLBACK",
            layers = listOf(
                DeveloperLayerSnapshot("photo-1", "Foto", "IMAGE", 4, true, true, "images/photo.png")
            )
        )

        val json = JSONObject(report.toJson())
        assertTrue(json.getJSONObject("project").getBoolean("activeProjectPresent"))
        assertTrue(json.getJSONObject("photo").getBoolean("photoLayerPresent"))
        assertEquals(4, json.getJSONObject("photo").getInt("zIndex"))
        assertEquals("VECTOR_FALLBACK", json.getJSONObject("avatar").getString("renderPath"))
        assertEquals(1, json.getJSONArray("sceneLayers").length())
        assertEquals("photo-1", json.getJSONArray("sceneLayers").getJSONObject(0).getString("id"))
    }

    @Test
    fun serializesMissingValuesAsJsonNullAndEmptyLayersAsArray() {
        val json = JSONObject(DeveloperDiagnosticsSnapshot().toJson())
        val project = json.getJSONObject("project")
        val photo = json.getJSONObject("photo")
        val face = json.getJSONObject("face")
        assertFalse(project.getBoolean("activeProjectPresent"))
        assertTrue(project.isNull("id"))
        assertTrue(photo.isNull("width"))
        assertTrue(photo.isNull("zIndex"))
        assertTrue(face.isNull("width"))
        assertTrue(face.isNull("faceCutterReturnedBitmap"))
        assertEquals(0, json.getJSONArray("sceneLayers").length())
    }

    @Test
    fun reportsLegacyAndModularCharacterLibrariesIndependently() {
        val report = DeveloperDiagnosticsSnapshot(
            pieceLibraryInitialized = false,
            pieceManifestFound = false,
            pngPiecesAvailable = false,
            characterLayerLibraryInitialized = true,
            characterManifestSource = "ASSET_MANIFEST",
            builtinVectorPartsAvailable = true,
            characterPngAssetsAvailable = false,
            avatarRenderPath = "MODULAR_LAYERS"
        )

        val avatar = JSONObject(report.toJson()).getJSONObject("avatar")
        assertFalse(avatar.getBoolean("pieceLibraryInitialized"))
        assertFalse(avatar.getBoolean("pieceManifestFound"))
        assertFalse(avatar.getBoolean("pngPiecesAvailable"))
        assertTrue(avatar.getBoolean("characterLayerLibraryInitialized"))
        assertEquals("ASSET_MANIFEST", avatar.getString("characterManifestSource"))
        assertTrue(avatar.getBoolean("builtinVectorPartsAvailable"))
        assertFalse(avatar.getBoolean("characterPngAssetsAvailable"))
        assertEquals("MODULAR_LAYERS", avatar.getString("renderPath"))
    }

}
