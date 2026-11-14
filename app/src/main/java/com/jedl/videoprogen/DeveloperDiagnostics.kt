package com.jedl.videoprogen

import org.json.JSONArray
import org.json.JSONObject

data class DeveloperLayerSnapshot(
    val id: String,
    val name: String,
    val type: String,
    val zIndex: Int,
    val visible: Boolean,
    val activeAtCapture: Boolean,
    val assetPath: String?
)

data class DeveloperDiagnosticsSnapshot(
    val activeProjectPresent: Boolean = false,
    val projectId: String? = null,
    val projectName: String? = null,
    val activeSceneId: String? = null,
    val photoBitmapLoaded: Boolean = false,
    val photoBitmapWidth: Int? = null,
    val photoBitmapHeight: Int? = null,
    val photoBitmapRecycled: Boolean? = null,
    val photoLayerPresent: Boolean = false,
    val photoLayerZIndex: Int? = null,
    val photoAssetPath: String? = null,
    val faceCutterExecuted: Boolean = false,
    val faceBitmapValid: Boolean = false,
    val faceBitmapWidth: Int? = null,
    val faceBitmapHeight: Int? = null,
    val facePassedToAvatarRenderer: Boolean = false,
    val pieceLibraryInitialized: Boolean = false,
    val pieceManifestFound: Boolean = false,
    val pngPiecesAvailable: Boolean = false,
    val avatarRenderPath: String = "VECTOR_FALLBACK",
    val layers: List<DeveloperLayerSnapshot> = emptyList()
) {
    fun toJson(): String {
        val project = JSONObject()
            .put("activeProjectPresent", activeProjectPresent)
            .putNullable("id", projectId)
            .putNullable("name", projectName)
            .putNullable("activeSceneId", activeSceneId)

        val photo = JSONObject()
            .put("bitmapLoaded", photoBitmapLoaded)
            .putNullable("width", photoBitmapWidth)
            .putNullable("height", photoBitmapHeight)
            .putNullable("bitmapRecycled", photoBitmapRecycled)
            .put("photoLayerPresent", photoLayerPresent)
            .putNullable("zIndex", photoLayerZIndex)
            .putNullable("assetPath", photoAssetPath)

        val face = JSONObject()
            .put("faceCutterExecuted", faceCutterExecuted)
            .put("bitmapValid", faceBitmapValid)
            .putNullable("width", faceBitmapWidth)
            .putNullable("height", faceBitmapHeight)
            .put("passedToAvatarRenderer", facePassedToAvatarRenderer)

        val avatar = JSONObject()
            .put("pieceLibraryInitialized", pieceLibraryInitialized)
            .put("pieceManifestFound", pieceManifestFound)
            .put("pngPiecesAvailable", pngPiecesAvailable)
            .put("renderPath", avatarRenderPath)

        val layerArray = JSONArray()
        layers.sortedBy { it.zIndex }.forEach { layer ->
            layerArray.put(
                JSONObject()
                    .put("id", layer.id)
                    .put("name", layer.name)
                    .put("type", layer.type)
                    .put("zIndex", layer.zIndex)
                    .put("visible", layer.visible)
                    .put("activeAtCapture", layer.activeAtCapture)
                    .putNullable("assetPath", layer.assetPath)
            )
        }

        return JSONObject()
            .put("diagnosticVersion", "F2.3")
            .put("project", project)
            .put("photo", photo)
            .put("face", face)
            .put("avatar", avatar)
            .put("sceneLayers", layerArray)
            .toString(2)
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}
