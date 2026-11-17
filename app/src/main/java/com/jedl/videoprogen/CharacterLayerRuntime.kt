package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import org.json.JSONObject

/**
 * Runtime contract for independently replaceable character artwork.
 * Pivots are normalized to each PNG's dimensions; zIndex is global back-to-front order.
 */
enum class CharacterLayerSlot {
    HEAD, EAR_L, EAR_R, EYES, EYEBROWS, NOSE, MOUTH, FACIAL_HAIR,
    HAIR_BACK, HAIR_FRONT, HAT_BACK, HAT_FRONT,
    NECK, TORSO, SHIRT, PANTS,
    UPPER_ARM_L, UPPER_ARM_R, SLEEVE_L, SLEEVE_R,
    FOREARM_L, FOREARM_R, HAND_L, HAND_R,
    THIGH_L, THIGH_R, SHIN_L, SHIN_R, FOOT_L, FOOT_R,
    SHOE_L, SHOE_R, GLASSES, CHAIN, MEDAL, WATCH,
    STICK_L, STICK_R, DRUM, DRUM_HEAD, DRUM_STAND
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
    val tintChannel: String? = null,
    val heightUnits: Float = widthUnits,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
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
) {
    companion object {
        /** Keeps the existing AvatarSpec controls functional on the modular renderer. */
        fun fromAvatarSpec(spec: AvatarSpec): CharacterLayerAppearance {
            val selected = mutableMapOf<CharacterLayerSlot, String>()
            when (spec.hairStyle) {
                0 -> selected[CharacterLayerSlot.HAIR_FRONT] = "hair_short"
                1 -> selected[CharacterLayerSlot.HAIR_FRONT] = "hair_afro"
                2 -> {
                    selected[CharacterLayerSlot.HAIR_FRONT] = ""
                    selected[CharacterLayerSlot.HAT_FRONT] = "cap_front"
                    selected[CharacterLayerSlot.HAT_BACK] = "hat_back"
                }
                3 -> selected[CharacterLayerSlot.HAIR_FRONT] = ""
                4 -> selected[CharacterLayerSlot.HAIR_FRONT] = "hair_long"
                else -> selected[CharacterLayerSlot.HAIR_FRONT] = "hair_front"
            }
            if (spec.hairStyle != 2) {
                selected[CharacterLayerSlot.HAT_FRONT] = ""
                selected[CharacterLayerSlot.HAT_BACK] = ""
            }
            selected[CharacterLayerSlot.FACIAL_HAIR] =
                if (spec.beard) "beard_short" else ""
            return CharacterLayerAppearance(
                selectedParts = selected,
                colors = mapOf(
                    "skin" to spec.skin, "hair" to spec.hair, "shirt" to spec.shirt,
                    "pants" to spec.pants, "shoes" to spec.shoes
                )
            )
        }
    }
}

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
            val normalizedPath = part.file.replace(92.toChar(), '/')
            val builtin = normalizedPath.startsWith("builtin://") &&
                normalizedPath.removePrefix("builtin://").matches(Regex("[a-z0-9_]+"))
            val safePng = normalizedPath.isNotBlank() && !normalizedPath.startsWith("/") &&
                normalizedPath.split('/').none { it == ".." } &&
                normalizedPath.endsWith(".png", ignoreCase = true)
            if (!builtin && !safePng) errors += "Unsafe or unsupported artwork path for ${part.id}"
            if (!part.pivotX.isFinite() || part.pivotX !in 0f..1f ||
                !part.pivotY.isFinite() || part.pivotY !in 0f..1f
            ) errors += "Pivot must be finite and normalized for ${part.id}"
            if (!part.widthUnits.isFinite() || part.widthUnits <= 0f) {
                errors += "widthUnits must be positive and finite for ${part.id}"
            }
            if (!part.heightUnits.isFinite() || part.heightUnits <= 0f ||
                !part.offsetX.isFinite() || !part.offsetY.isFinite()
            ) errors += "Invalid dimensions or offsets for ${part.id}"
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

    fun starterManifest(): CharacterLayerManifest {
        fun part(
            id: String, slot: CharacterLayerSlot, bone: String, w: Float, h: Float,
            z: Int, px: Float = .5f, py: Float = 0f,
            ox: Float = 0f, oy: Float = 0f, tint: String? = null
        ) = CharacterLayerDef(
            id, slot, bone, "builtin://$id", px, py, w, z, tint, h, ox, oy
        )
        val parts = listOf(
            part("hat_back", CharacterLayerSlot.HAT_BACK, "head", .20f, .12f, 4, .5f, 0f, 0f, .12f, "shirt"),
            part("hair_back", CharacterLayerSlot.HAIR_BACK, "head", .18f, .20f, 5),
            part("hair_short", CharacterLayerSlot.HAIR_FRONT, "head", .18f, .10f, 60, .5f, 0f, 0f, .12f, "hair"),
            part("hair_afro", CharacterLayerSlot.HAIR_FRONT, "head", .20f, .13f, 60, .5f, 0f, 0f, .11f, "hair"),
            part("hair_long", CharacterLayerSlot.HAIR_FRONT, "head", .20f, .19f, 60, .5f, 0f, 0f, .02f, "hair"),
            part("ear_left", CharacterLayerSlot.EAR_L, "head", .055f, .065f, 12, .5f, .5f, -.073f, .105f, "skin"),
            part("ear_right", CharacterLayerSlot.EAR_R, "head", .055f, .065f, 12, .5f, .5f, .073f, .105f, "skin"),
            part("neck", CharacterLayerSlot.NECK, "head", .065f, .055f, 15, .5f, 1f, 0f, 0f, "skin"),
            part("torso_base", CharacterLayerSlot.TORSO, "torso", .245f, .31f, 20, .5f, 0f, 0f, 0f, "shirt"),
            part("pants_base", CharacterLayerSlot.PANTS, "torso", .205f, .15f, 21, .5f, 1f, 0f, 0f, tint = "pants"),
            part("upper_arm_l", CharacterLayerSlot.UPPER_ARM_L, "armUL", .075f, .15f, 25, .5f, 0f, 0f, 0f, "skin"),
            part("upper_arm_r", CharacterLayerSlot.UPPER_ARM_R, "armUR", .075f, .15f, 25, .5f, 0f, 0f, 0f, "skin"),
            part("sleeve_l", CharacterLayerSlot.SLEEVE_L, "armUL", .095f, .075f, 26, .5f, 0f, 0f, 0f, "shirt"),
            part("sleeve_r", CharacterLayerSlot.SLEEVE_R, "armUR", .095f, .075f, 26, .5f, 0f, 0f, 0f, "shirt"),
            part("forearm_l", CharacterLayerSlot.FOREARM_L, "foreArmL", .068f, .14f, 28, .5f, 0f, 0f, 0f, "skin"),
            part("forearm_r", CharacterLayerSlot.FOREARM_R, "foreArmR", .068f, .14f, 28, .5f, 0f, 0f, 0f, "skin"),
            part("thigh_l", CharacterLayerSlot.THIGH_L, "thighL", .085f, .19f, 29, .5f, 0f, 0f, 0f, tint = "pants"),
            part("thigh_r", CharacterLayerSlot.THIGH_R, "thighR", .085f, .19f, 29, .5f, 0f, 0f, tint = "pants"),
            part("shin_l", CharacterLayerSlot.SHIN_L, "shinL", .072f, .18f, 30, .5f, 0f, 0f, 0f, tint = "pants"),
            part("shin_r", CharacterLayerSlot.SHIN_R, "shinR", .072f, .18f, 30, .5f, 0f, 0f, tint = "pants"),
            part("foot_l", CharacterLayerSlot.FOOT_L, "shinL", .095f, .055f, 33, .5f, 0f, 0f, .145f, "skin"),
            part("foot_r", CharacterLayerSlot.FOOT_R, "shinR", .095f, .055f, 33, .5f, 0f, 0f, .145f, "skin"),
            part("shoe_l", CharacterLayerSlot.SHOE_L, "shinL", .105f, .065f, 34, .5f, 0f, 0f, .145f, "shoes"),
            part("shoe_r", CharacterLayerSlot.SHOE_R, "shinR", .105f, .065f, 34, .5f, 0f, 0f, .145f, "shoes"),
            part("hand_l", CharacterLayerSlot.HAND_L, "foreArmL", .060f, .075f, 35, .5f, 0f, 0f, .105f, "skin"),
            part("hand_r", CharacterLayerSlot.HAND_R, "foreArmR", .060f, .075f, 35, .5f, 0f, 0f, .105f, "skin"),
            part("head_base", CharacterLayerSlot.HEAD, "head", .18f, .20f, 40, .5f, 0f, 0f, 0f, "skin"),
            part("eyes_pair", CharacterLayerSlot.EYES, "head", .13f, .10f, 50, .5f, .5f, 0f, .065f),
            part("eyes_closed", CharacterLayerSlot.EYES, "head", .13f, .10f, 50, .5f, .5f, 0f, .065f),
            part("brows_pair", CharacterLayerSlot.EYEBROWS, "head", .13f, .08f, 51, .5f, .5f, 0f, .052f, "hair"),
            part("nose_small", CharacterLayerSlot.NOSE, "head", .045f, .07f, 52, .5f, .5f, 0f, .085f),
            part("mouth_smile", CharacterLayerSlot.MOUTH, "head", .075f, .055f, 53, .5f, .5f, 0f, .12f),
            part("mouth_neutral", CharacterLayerSlot.MOUTH, "head", .075f, .055f, 53, .5f, .5f, 0f, .12f),
            part("mouth_open", CharacterLayerSlot.MOUTH, "head", .075f, .065f, 53, .5f, .5f, 0f, .12f),
            part("beard_short", CharacterLayerSlot.FACIAL_HAIR, "head", .12f, .075f, 55, .5f, 0f, 0f, .02f, "hair"),
            part("hair_front", CharacterLayerSlot.HAIR_FRONT, "head", .18f, .10f, 60, .5f, 0f, 0f, 0f, "hair"),
            part("cap_front", CharacterLayerSlot.HAT_FRONT, "head", .20f, .105f, 65, .5f, 0f, 0f, .12f, "shirt"),
            part("glasses", CharacterLayerSlot.GLASSES, "head", .15f, .075f, 70, .5f, .5f, 0f, .065f),
            part("chain", CharacterLayerSlot.CHAIN, "torso", .13f, .18f, 72, .5f, 0f, 0f, .10f),
            part("medal", CharacterLayerSlot.MEDAL, "torso", .07f, .08f, 73, .5f, .5f, 0f, .23f),
            part("watch", CharacterLayerSlot.WATCH, "foreArmR", .045f, .065f, 74, .5f, .5f, 0f, .07f),
            part("stick_l", CharacterLayerSlot.STICK_L, "foreArmL", .025f, .18f, 80, .5f, .5f, 0f, .045f),
            part("stick_r", CharacterLayerSlot.STICK_R, "foreArmR", .025f, .18f, 80, .5f, .5f, 0f, .045f),
            part("drum_shell", CharacterLayerSlot.DRUM, "torso", .22f, .18f, 75, .5f, .5f, .17f, .19f),
            part("drum_head", CharacterLayerSlot.DRUM_HEAD, "torso", .22f, .055f, 76, .5f, .5f, .17f, .19f),
            part("drum_stand", CharacterLayerSlot.DRUM_STAND, "torso", .16f, .15f, 74, .5f, 0f, .17f, .34f)
        )
        val defaults = (parts.associate { it.slot to it.id } - CharacterLayerSlot.FACIAL_HAIR) + mapOf(
            CharacterLayerSlot.HAIR_FRONT to "hair_front",
            CharacterLayerSlot.EYES to "eyes_pair",
            CharacterLayerSlot.MOUTH to "mouth_smile"
        )
        return CharacterLayerManifest("jedl_master_starter", 1, parts, defaults)
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
                tintChannel = item.optString("tintChannel").takeIf { it.isNotBlank() && it != "null" },
                heightUnits = item.optDouble("heightUnits", item.getDouble("widthUnits")).toFloat(),
                offsetX = item.optDouble("offsetX", 0.0).toFloat(),
                offsetY = item.optDouble("offsetY", 0.0).toFloat()
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
    @Volatile private var manifestSource: String = "NOT_INITIALIZED"
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    @Synchronized fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        val loaded = try {
            val json = context.assets.open("$ASSET_DIR/manifest.json")
                .bufferedReader().use { it.readText() }
            CharacterLayerContract.parse(json)?.takeIf {
                CharacterLayerContract.validate(it).isValid
            }
        } catch (_: Exception) {
            null
        }
        if (loaded != null) {
            manifest = loaded
            manifestSource = "ASSET_MANIFEST"
        } else {
            manifest = CharacterLayerContract.starterManifest()
            manifestSource = "BUILTIN_FALLBACK"
        }
    }

    fun isInitialized(): Boolean = appContext != null

    fun isReady(): Boolean = manifest != null

    fun manifestSource(): String = manifestSource

    fun builtinVectorPartsAvailable(): Boolean {
        val current = manifest ?: return false
        val builtinParts = current.parts.filter { it.file.startsWith("builtin://") }
        return builtinParts.isNotEmpty() && builtinParts.all {
            CharacterPartPainter.supportedPartIds.contains(it.file.removePrefix("builtin://"))
        }
    }

    fun declaredPngAssetsAvailable(): Boolean {
        val ctx = appContext ?: return false
        val current = manifest ?: return false
        val pngParts = current.parts.filterNot { it.file.startsWith("builtin://") }
        return pngParts.isNotEmpty() && pngParts.all { part ->
            try {
                ctx.assets.open("$ASSET_DIR/${part.file}").use { true }
            } catch (_: Exception) {
                false
            }
        }
    }

    fun builtinPartCount(): Int =
        manifest?.parts?.count { it.file.startsWith("builtin://") } ?: 0

    fun pngPartCount(): Int =
        manifest?.parts?.count { !it.file.startsWith("builtin://") } ?: 0

    @Synchronized fun draw(
        canvas: Canvas,
        solved: Map<String, BonePose>,
        appearance: CharacterLayerAppearance = CharacterLayerAppearance()
    ): Boolean {
        val ctx = appContext ?: return false
        val current = manifest ?: return false
        val layers = CharacterLayerContract.resolve(current, appearance)
        if (layers.isEmpty() || layers.any { solved[it.boneId] == null }) return false
        val prepared = layers.map { layer ->
            if (layer.file.startsWith("builtin://")) layer to null
            else layer to (bitmapFor(ctx, layer.file) ?: return false)
        }
        for ((layer, bitmap) in prepared) {
            val pose = solved[layer.boneId] ?: return false
            val save = canvas.save()
            canvas.concat(pose.matrix)
            if (layer.file.startsWith("builtin://")) {
                canvas.translate(
                    layer.offsetX - layer.pivotX * layer.widthUnits,
                    layer.offsetY - layer.pivotY * layer.heightUnits
                )
                canvas.scale(layer.widthUnits, layer.heightUnits)
                if (!CharacterPartPainter.draw(
                    canvas, layer.file.removePrefix("builtin://"), appearance.colors
                )) {
                    canvas.restoreToCount(save)
                    return false
                }
            } else {
                val image = bitmap ?: run {
                    canvas.restoreToCount(save)
                    return false
                }
                canvas.translate(
                    layer.offsetX - layer.pivotX * layer.widthUnits,
                    layer.offsetY - layer.pivotY * layer.heightUnits
                )
                canvas.scale(
                    layer.widthUnits / image.width.toFloat(),
                    layer.heightUnits / image.height.toFloat()
                )
                paint.colorFilter = appearance.colors[layer.tintChannel]?.let {
                    PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN)
                }
                canvas.drawBitmap(image, 0f, 0f, paint)
                paint.colorFilter = null
            }
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
            if (!bitmap.hasAlpha()) {
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
        manifestSource = "NOT_INITIALIZED"
    }
}
