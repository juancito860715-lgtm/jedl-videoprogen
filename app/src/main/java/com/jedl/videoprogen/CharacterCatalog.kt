package com.jedl.videoprogen

/**
 * Independent customization slots for the JEDL master character.
 *
 * A slot is a semantic layer, not a bone: several slots may attach to the same
 * bone and each slot may expose multiple interchangeable parts.
 */
enum class CharacterSlot {
    SKIN,
    FACE,
    EYES,
    EYEBROWS,
    MOUTH,
    HAIR,
    HAT,
    TOP,
    BOTTOM,
    HANDS,
    SHOES,
    ACCESSORY
}

data class CharacterPartDefinition(
    val id: String,
    val slot: CharacterSlot,
    /** Relative path below the character asset root; absolute and traversal paths are forbidden. */
    val assetPath: String,
    val zIndex: Int,
    /** Optional rig bone attachment; kept as an ID so the catalog remains independent of the rig implementation. */
    val boneId: String? = null,
    /** Independently recolorable regions, e.g. hat.crown and hat.panel. */
    val colorChannels: Set<String> = emptySet(),
    val view: String = "three_quarter"
)

data class CharacterCatalog(
    val id: String,
    val version: Int,
    val parts: List<CharacterPartDefinition>,
    val defaults: Map<CharacterSlot, String>
)

data class CharacterAppearanceDocument(
    val catalogId: String,
    val selectedParts: Map<CharacterSlot, String>,
    /** ARGB values keyed by semantic region, not by whole image. */
    val colors: Map<String, Int>,
    val schemaVersion: Int = 1
)

data class CharacterCatalogValidation(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object CharacterCatalogValidator {
    private val idPattern = Regex("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")
    private val colorChannelPattern = Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,63}")

    fun validate(catalog: CharacterCatalog): CharacterCatalogValidation {
        val errors = mutableListOf<String>()
        if (!idPattern.matches(catalog.id)) errors += "Invalid catalog id"
        if (catalog.version < 1) errors += "Catalog version must be positive"

        val duplicateIds = catalog.parts.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) errors += "Duplicate part ids: ${duplicateIds.joinToString()}"

        val byId = catalog.parts.associateBy { it.id }
        catalog.parts.forEach { part ->
            if (!idPattern.matches(part.id)) errors += "Invalid part id: ${part.id}"
            if (!isSafeAssetPath(part.assetPath)) errors += "Unsafe asset path for ${part.id}"
            if (part.view.isBlank()) errors += "Missing view for ${part.id}"
            if (part.colorChannels.any { !colorChannelPattern.matches(it) }) {
                errors += "Invalid color channel for ${part.id}"
            }
        }

        catalog.defaults.forEach { (slot, partId) ->
            val part = byId[partId]
            if (part == null) {
                errors += "Unknown default part $partId for slot $slot"
            } else if (part.slot != slot) {
                errors += "Default part $partId belongs to ${part.slot}, not $slot"
            }
        }
        return CharacterCatalogValidation(errors.distinct())
    }

    internal fun isSafeAssetPath(path: String): Boolean {
        if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
        if (path.contains('\\')) return false
        val segments = path.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return false
        return segments.last().endsWith(".png", ignoreCase = true) ||
            segments.last().endsWith(".webp", ignoreCase = true)
    }
}

object CharacterAppearanceValidator {
    fun validate(
        appearance: CharacterAppearanceDocument,
        catalog: CharacterCatalog
    ): List<String> {
        val errors = mutableListOf<String>()
        if (appearance.schemaVersion != 1) errors += "Unsupported appearance schema version"
        if (appearance.catalogId != catalog.id) errors += "Appearance references a different catalog"

        val partsById = catalog.parts.associateBy { it.id }
        appearance.selectedParts.forEach { (slot, partId) ->
            val part = partsById[partId]
            if (part == null) {
                errors += "Unknown selected part $partId for slot $slot"
            } else if (part.slot != slot) {
                errors += "Selected part $partId belongs to ${part.slot}, not $slot"
            }
        }

        appearance.colors.forEach { (channel, argb) ->
            if (channel.isBlank() || channel.contains("..") || channel.startsWith('/')) {
                errors += "Invalid color channel $channel"
            }
            if ((argb ushr 24) == 0) errors += "Color channel $channel is fully transparent"
        }
        return errors.distinct()
    }
}
