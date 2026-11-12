package com.jedl.videoprogen

import java.util.UUID

/**
 * Documento editable del proyecto. Las coordenadas de Transform2D son
 * normalizadas (0..1 respecto al lienzo), no píxeles del dispositivo.
 *
 * El documento es deliberadamente independiente de Android Views/Compose
 * para poder probarlo y reutilizarlo desde preview, editor y exportación.
 */
data class ProjectDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Proyecto sin título",
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val modifiedAtMillis: Long = System.currentTimeMillis(),
    val width: Int = 720,
    val height: Int = 1280,
    val frameRate: Int = 30,
    val scenes: List<SceneDocument> = listOf(SceneDocument())
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MIN_CANVAS_SIZE = 64
        const val MAX_CANVAS_SIZE = 1920
    }
}

data class SceneDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Escena 1",
    val durationMillis: Long = 5_000L,
    val backgroundColor: Int = 0xFF101116.toInt(),
    val layers: List<LayerDocument> = emptyList(),
    val animationClips: List<AnimationClipDocument> = emptyList()
)

/** Estado de animación serializable; no depende de clases Android del motor. */
data class AnimationClipDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Animación",
    val targetLayerId: String,
    val durationMillis: Long = 5_000L,
    val loop: Boolean = false,
    val keyframes: List<AnimationKeyframeDocument> = emptyList()
)

data class AnimationKeyframeDocument(
    val timeMillis: Long,
    val boneAngles: Map<String, Float>,
    val rootBob: Float = 0f,
    val rootSway: Float = 0f
)

enum class LayerType {
    BACKGROUND, IMAGE, CHARACTER, TEXT, VIDEO, AUDIO, SUBTITLE
}

/**
 * assetPath siempre es una ruta relativa dentro de la carpeta del proyecto.
 * Nunca debe contener rutas absolutas ni segmentos '..'.
 */
data class LayerDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Capa",
    val type: LayerType = LayerType.IMAGE,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val transform: Transform2D = Transform2D(),
    val assetPath: String? = null,
    val text: String = "",
    val startMillis: Long = 0L,
    val durationMillis: Long = 5_000L,
    val zIndex: Int = 0
)

data class Transform2D(
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f
)

/** Validación compartida por el repositorio y el editor antes de guardar. */
object ProjectDocumentValidator {
    private val safeId = Regex("^[a-fA-F0-9-]{16,64}$")

    fun validate(project: ProjectDocument): String? {
        if (!safeId.matches(project.id)) return "Identificador de proyecto no válido"
        if (project.name.isBlank() || project.name.length > 120) return "Nombre de proyecto no válido"
        if (project.schemaVersion != ProjectDocument.CURRENT_SCHEMA_VERSION) return "Versión de proyecto no compatible"
        if (project.width !in ProjectDocument.MIN_CANVAS_SIZE..ProjectDocument.MAX_CANVAS_SIZE ||
            project.height !in ProjectDocument.MIN_CANVAS_SIZE..ProjectDocument.MAX_CANVAS_SIZE) {
            return "Dimensiones del lienzo fuera del rango permitido"
        }
        if (project.width % 2 != 0 || project.height % 2 != 0) return "El lienzo debe tener dimensiones pares"
        if (project.frameRate !in 1..60) return "FPS fuera del rango permitido"
        if (project.scenes.isEmpty() || project.scenes.size > 500) return "Cantidad de escenas no válida"

        val sceneIds = HashSet<String>()
        for (scene in project.scenes) {
            if (!safeId.matches(scene.id) || !sceneIds.add(scene.id)) return "ID de escena no válido o duplicado"
            if (scene.name.isBlank() || scene.name.length > 120) return "Nombre de escena no válido"
            if (scene.durationMillis !in 1L..3_600_000L) return "Duración de escena no válida"
            if (scene.layers.size > 2_000) return "Demasiadas capas en una escena"
            val layerIds = HashSet<String>()
            for (layer in scene.layers) {
                if (!safeId.matches(layer.id) || !layerIds.add(layer.id)) return "ID de capa no válido o duplicado"
                if (layer.name.isBlank() || layer.name.length > 120) return "Nombre de capa no válido"
                if (!layer.opacity.isFinite() || layer.opacity !in 0f..1f) return "Opacidad no válida"
                val t = layer.transform
                if (!t.x.isFinite() || !t.y.isFinite() ||
                    !t.scaleX.isFinite() || !t.scaleY.isFinite() ||
                    !t.rotationDegrees.isFinite()) return "Transformación no válida"
                if (t.x !in -4f..4f || t.y !in -4f..4f ||
                    t.scaleX !in 0.01f..20f || t.scaleY !in 0.01f..20f ||
                    t.rotationDegrees !in -36_000f..36_000f) return "Transformación fuera de límites"
                if (layer.startMillis < 0L || layer.durationMillis !in 1L..3_600_000L) {
                    return "Tiempo de capa no válido"
                }
                val path = layer.assetPath
                if (path != null && !isSafeRelativeAssetPath(path)) return "Ruta de recurso no segura"
            }
            val clipIds = HashSet<String>()
            for (clip in scene.animationClips) {
                if (!safeId.matches(clip.id) || !clipIds.add(clip.id)) return "ID de animación no válido o duplicado"
                if (clip.name.isBlank() || clip.name.length > 120) return "Nombre de animación no válido"
                if (clip.durationMillis !in 1L..3_600_000L) return "Duración de animación no válida"
                if (scene.layers.none { it.id == clip.targetLayerId && it.type == LayerType.CHARACTER }) return "La animación debe apuntar a una capa de personaje existente"
                if (clip.keyframes.isEmpty() || clip.keyframes.size > 10_000) return "Cantidad de keyframes no válida"
                var previousTime = -1L
                for (keyframe in clip.keyframes) {
                    if (keyframe.timeMillis !in 0L..clip.durationMillis || keyframe.timeMillis <= previousTime) return "Orden o tiempo de keyframes no válido"
                    previousTime = keyframe.timeMillis
                    if (!keyframe.rootBob.isFinite() || !keyframe.rootSway.isFinite()) return "Desplazamiento raíz no válido"
                    if (keyframe.rootBob !in -4f..4f || keyframe.rootSway !in -4f..4f) return "Desplazamiento raíz fuera de límites"
                    if (keyframe.boneAngles.size > 128 || keyframe.boneAngles.any { (bone, angle) -> bone.isBlank() || bone.length > 64 || !angle.isFinite() || angle !in -360f..360f }) return "Ángulos de huesos no válidos"
                }
            }
        }
        return null
    }

    fun isSafeRelativeAssetPath(path: String): Boolean {
        if (path.isBlank() || path.length > 512 || path.startsWith("/") || path.startsWith("\\")) return false
        if (path.contains('\\')) return false
        val segments = path.split('/')
        return segments.all { it.isNotBlank() && it != "." && it != ".." } &&
            !Regex("^[A-Za-z]:").containsMatchIn(path)
    }
}
