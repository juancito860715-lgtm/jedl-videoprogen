package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import org.json.JSONObject

/**
 * Runtime contract for independently replaceable character artwork.
 * Pivots are normalized to each PNG's dimensions; zIndex is global back-to-front order.
 */
enum class CharacterLayerSlot {
    HEAD, EAR_L, EAR_R, EYES, EYEBROWS, NOSE, MOUTH,
    HAIR_BACK, HAIR_FRONT, HAT_BACK, HAT_FRONT,
    NECK, TORSO, SHIRT, PANTS,
    UPPER_ARM_L, UPPER_ARM_R, SLEEVE_L, SLEEVE_R,
    FOREARM_L, FOREARM_R, HAND_L, HAND_R,
    THIGH_L, THIGH_R, SHIN_L, SHIN_R, FOOT_L, FOOT_R,
    SHOE_L, SHOE_R, GLASSES, CHAIN, MEDAL, WATCH,
    STICK_L, STICK_R, DRUM, DRUM_STAND
}

data class CharacterLayerDef(
    val id: String,
    val slot: CharacterLayerSlot,
    val boneId: String,
    val file: String,
    val pivotX: Float,
    val pivotY: Float,
    val widthUnits: Float,
    val zIndex: Int,
    val tintChannel: String? = null
)

data class CharacterLayerManifest(
    val id: String,
    val version: Int,
    val parts: List<CharacterLayerDef>,
    val defaults: Map<CharacterLayerSlot, String>
)

data class CharacterLayerAppearance(
    val selectedParts: Map<CharacterLayerSlot, String> = emptyMap(),
    val colors: Map<String, Int> = emptyMap()
)

data class CharacterLayerValidation(val errors: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

object CharacterLayerContract {
    val supportedBones = setOf(
        "torso", "head", "armUL", "foreArmL", "armUR", "foreArmR",
        "thighL", "shinL", "thighR", "shinR"
    )

    fun validate(manifest: CharacterLayerManifest): CharacterLayerValidation {
        val errors = mutableListOf<String>()
        if (manifest.id.isBlank()) errors += "Manifest id must not be blank"
        if (manifest.version < 1) errors += "Manifest version must be positive"
        val ids = manifest.parts.map { it.id }
        if (ids.any { it.isBlank() }) errors += "Part ids must not be blank"
        if (ids.size != ids.toSet().size) errors += "Duplicate part id"
        manifest.parts.forEach { part ->
            if (part.boneId !in supportedBones) errors += "Unknown bone for ${part.id}: ${part.boneId}"
            val normalizedPath = part.file.replace('\\\\', '/')
            if (normalizedPath.isBlank() || normalizedPath.startsWith("/") ||
                normalizedPath.split('/').any { it == ".." } ||
                !normalizedPath.endsWith(".png", ignoreCase = true)
            ) errors += "Unsafe or unsupported PNG path for ${part.id}"
            if (!part.pivotX.isFinite() || part.pivotX !in 0f..1f ||
                !part.pivotY.isFinite() || part.pivotY !in 0f..1f
            ) errors += "Pivot must be finite and normalized for ${part.id}"
            if (!part.widthUnits.isFinite() || part.widthUnits <= 0f) {
                errors += "widthUnits must be positive and finite for ${part.id}"
            }
            if (part.tintChannel != null && part.tintChannel.isBlank()) {
                errors += "Tint channel must not be blank for ${part.id}"
            }
        }
        val byId = manifest.parts.associateBy { it.id }
        manifest.defaults.forEach { (slot, id) ->
            val part = byId[id]
            if (part == null) errors += "Default part $id for $slot does not exist"
            else if (part.slot != slot) errors += "Default part $id belongs to ${part.slot}, not $slot"
        }
        return CharacterLayerValidation(errors.distinct())
    }

    fun resolve(
        manifest: CharacterLayerManifest,
        appearance: CharacterLayerAppearance
    ): List<CharacterLayerDef> {
        val byId = manifest.parts.associateBy { it.id }
        val selected = manifest.defaults + appearance.selectedParts
        return selected.mapNotNull { (slot, id) ->
            byId[id]?.takeIf { it.slot == slot }
        }.sortedBy { it.zIndex }
    }

    fun parse(json: String): CharacterLayerManifest? = try {
        val root = JSONObject(json)
        val partsJson = root.getJSONArray("parts")
        val parts = (0 until partsJson.length()).map { index ->
            val item = partsJson.getJSONObject(index)
            CharacterLayerDef(
                id = item.getString("id"),
                slot = CharacterLayerSlot.valueOf(item.getString("slot")),
                boneId = item.getString("boneId"),
                file = item.getString("file"),
                pivotX = item.getDouble("pivotX").toFloat(),
                pivotY = item.getDouble("pivotY").toFloat(),
                widthUnits = item.getDouble("widthUnits").toFloat(),
                zIndex = item.getInt("zIndex"),
                tintChannel = item.optString("tintChannel").takeIf { it.isNotBlank() && it != "null" }
            )
        }
        val defaultsJson = root.getJSONObject("defaults")
        val defaults = mutableMapOf<CharacterLayerSlot, String>()
        val keys = defaultsJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            defaults[CharacterLayerSlot.valueOf(key)] = defaultsJson.getString(key)
        }
        CharacterLayerManifest(
            id = root.getString("id"),
            version = root.getInt("version"),
            parts = parts,
            defaults = defaults
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * Loads optional layered PNG artwork. Missing/invalid art never prevents the
 * existing vector avatar from rendering. Draw calls reuse decoded bitmaps.
 */
object CharacterLayerLibrary {
    private const val ASSET_DIR = "characters/jedl_master_v1"
    @Volatile private var appContext: Context? = null
    @Volatile private var manifest: CharacterLayerManifest? = null
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    @Synchronized fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        manifest = try {
            val json = context.assets.open("$ASSET_DIR/manifest.json")
                .bufferedReader().use { it.readText() }
            CharacterLayerContract.parse(json)?.takeIf {
                CharacterLayerContract.validate(it).isValid
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isReady(): Boolean = manifest != null

    @Synchronized fun draw(
        canvas: Canvas,
        solved: Map<String, BonePose>,
        appearance: CharacterLayerAppearance = CharacterLayerAppearance()
    ): Boolean {
        val ctx = appContext ?: return false
        val current = manifest ?: return false
        val layers = CharacterLayerContract.resolve(current, appearance)
        if (layers.isEmpty()) return false
        for (layer in layers) {
            val pose = solved[layer.boneId] ?: continue
            val bitmap = bitmapFor(ctx, layer.file) ?: continue
            val width = layer.widthUnits
            val height = width * bitmap.height.toFloat() / bitmap.width.toFloat()
            val save = canvas.save()
            canvas.concat(pose.matrix)
            paint.colorFilter = appearance.colors[layer.tintChannel]?.let {
                PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
            }
            canvas.drawBitmap(
                bitmap, null,
                android.graphics.RectF(
                    -layer.pivotX * width,
                    -layer.pivotY * height,
                    (1f - layer.pivotX) * width,
                    (1f - layer.pivotY) * height
                ),
                paint
            )
            paint.colorFilter = null
            canvas.restoreToCount(save)
        }
        return true
    }

    @Synchronized private fun bitmapFor(context: Context, file: String): Bitmap? {
        bitmaps[file]?.let { return it }
        return try {
            val bitmap = context.assets.open("$ASSET_DIR/$file").use {
                BitmapFactory.decodeStream(it)
            } ?: return null
            if (bitmap.config != Bitmap.Config.ARGB_8888 && bitmap.hasAlpha().not()) {
                bitmap.recycle()
                return null
            }
            bitmaps[file] = bitmap
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized fun clearForTests() {
        bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps.clear()
        manifest = null
        appContext = null
    }
}
