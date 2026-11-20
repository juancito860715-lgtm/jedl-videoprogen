package com.jedl.videoprogen

import org.json.JSONObject

/**
 * Versioned, content-only resource contract shared by the editor, renderer,
 * project store and exporter. A manifest describes data; it never executes code.
 */
enum class EngineResourceKind {
    IMAGE, VECTOR_OBJECT, CHARACTER, AUDIO, MOTION, FONT
}

data class NormalizedPivot(
    val x: Float = 0.5f,
    val y: Float = 0.5f
)

data class EngineResourceManifest(
    val id: String,
    val kind: EngineResourceKind,
    val path: String,
    val width: Int? = null,
    val height: Int? = null,
    val pivot: NormalizedPivot? = null,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION
) {
    fun toJson(): String {
        val validationError = EngineResourceManifest.validate(this)
        require(validationError == null) { validationError ?: "Manifiesto no válido" }
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("id", id)
            put("kind", kind.name)
            put("path", path)
            if (width != null) put("width", width)
            if (height != null) put("height", height)
            if (pivot != null) put("pivot", JSONObject().apply {
                put("x", pivot.x.toDouble())
                put("y", pivot.y.toDouble())
            })
        }.toString(2)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        private val SAFE_RESOURCE_ID = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
        private const val MAX_DIMENSION = 8192

        private fun requiredString(json: JSONObject, key: String): String {
            val value = json.get(key)
            return value as? String
                ?: throw IllegalArgumentException("El campo $key debe ser texto")
        }

        private fun requiredNumber(json: JSONObject, key: String): Double {
            val value = json.get(key)
            require(value is Number) { "El campo $key debe ser numérico" }
            val number = value.toDouble()
            require(number.isFinite()) { "El campo $key debe ser finito" }
            return number
        }

        private fun requiredInt(json: JSONObject, key: String): Int {
            val number = requiredNumber(json, key)
            require(
                number % 1.0 == 0.0 &&
                    number >= Int.MIN_VALUE.toDouble() &&
                    number <= Int.MAX_VALUE.toDouble()
            ) { "El campo $key debe ser un entero válido" }
            return number.toInt()
        }

        private fun optionalInt(json: JSONObject, key: String): Int? =
            if (!json.has(key) || json.isNull(key)) null else requiredInt(json, key)

        /** Parse only known declarative fields; unknown resource kinds fail closed. */
        fun fromJson(source: String): EngineResourceManifest {
            require(source.length <= 64 * 1024) { "Manifiesto demasiado grande" }
            val json = JSONObject(source)
            val version = requiredInt(json, "schemaVersion")
            require(version == CURRENT_SCHEMA_VERSION) { "Versión de manifiesto no compatible" }
            val kind = try {
                EngineResourceKind.valueOf(requiredString(json, "kind"))
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Tipo de recurso desconocido")
            }
            val pivot = if (!json.has("pivot") || json.isNull("pivot")) {
                null
            } else {
                val pivotObject = json.get("pivot") as? JSONObject
                    ?: throw IllegalArgumentException("El pivote debe ser un objeto")
                NormalizedPivot(
                    requiredNumber(pivotObject, "x").toFloat(),
                    requiredNumber(pivotObject, "y").toFloat()
                )
            }
            val manifest = EngineResourceManifest(
                id = requiredString(json, "id"),
                kind = kind,
                path = requiredString(json, "path"),
                width = optionalInt(json, "width"),
                height = optionalInt(json, "height"),
                pivot = pivot,
                schemaVersion = version
            )
            val error = validate(manifest)
            require(error == null) { error ?: "Manifiesto no válido" }
            return manifest
        }

        fun validate(manifest: EngineResourceManifest): String? {
            if (manifest.schemaVersion != CURRENT_SCHEMA_VERSION) return "Versión de manifiesto no compatible"
            if (!SAFE_RESOURCE_ID.matches(manifest.id)) return "ID de recurso no válido"
            if (!ProjectDocumentValidator.isSafeRelativeAssetPath(manifest.path)) return "Ruta de recurso no segura"

            val requiresDimensions = manifest.kind in setOf(
                EngineResourceKind.IMAGE,
                EngineResourceKind.VECTOR_OBJECT,
                EngineResourceKind.CHARACTER
            )
            val hasWidth = manifest.width != null
            val hasHeight = manifest.height != null
            if (hasWidth != hasHeight) return "Ancho y alto deben especificarse juntos"

            if (requiresDimensions && !hasWidth) {
                return "Faltan las dimensiones del recurso"
            }
            if (hasWidth) {
                val width = manifest.width!!
                val height = manifest.height!!
                if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) {
                    return "Dimensiones del recurso fuera de rango"
                }
            }

            manifest.pivot?.let {
                if (!it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f) {
                    return "Pivote normalizado fuera de rango"
                }
            }
            return null
        }
    }
}

/**
 * Deterministic scene clock. Export timestamps derive from frame index, never
 * from render duration or wall-clock time. Frame count rounds up the last
 * partial frame so the requested scene duration is not silently truncated.
 */
class SceneTimebase(
    val framesPerSecond: Int,
    val durationMillis: Long
) {
    init {
        require(framesPerSecond in 1..60) { "FPS fuera del rango permitido" }
        require(durationMillis in 1L..3_600_000L) { "Duración fuera del rango permitido" }
    }

    val frameCount: Long =
        (durationMillis * framesPerSecond + 999L) / 1_000L

    val durationUs: Long = durationMillis * 1_000L

    /** Presentation timestamp in microseconds for a frame or the end sentinel. */
    fun presentationTimeUs(frameIndex: Long): Long {
        require(frameIndex in 0L..frameCount) { "Índice de fotograma fuera de rango" }
        // The sentinel marks the exact scene boundary, including a partial last frame.
        if (frameIndex == frameCount) return durationUs
        return frameIndex * 1_000_000L / framesPerSecond
    }

    /**
     * Normalized scene position for rendering the given frame.
     * Uses its presentation timestamp, not frameIndex / frameCount, so a
     * partial final frame is rendered near the actual end of the scene.
     */
    fun normalizedProgressAt(frameIndex: Long): Float {
        val timestampUs = presentationTimeUs(frameIndex)
        return (timestampUs.toDouble() / durationUs.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    /** Frame containing the given timestamp, clamped to the scene's frame range. */
    fun frameIndexAt(timeUs: Long): Long {
        require(timeUs >= 0L) { "El tiempo no puede ser negativo" }
        if (timeUs >= durationUs) return frameCount
        val wholeSeconds = timeUs / 1_000_000L
        val remainingMicros = timeUs % 1_000_000L
        return (wholeSeconds * framesPerSecond + remainingMicros * framesPerSecond / 1_000_000L)
            .coerceIn(0L, frameCount)
    }
}
