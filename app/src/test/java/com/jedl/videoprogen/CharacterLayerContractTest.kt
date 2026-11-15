package com.jedl.videoprogen

import org.junit.Assert.*
import org.junit.Test

class CharacterLayerContractTest {
    private fun validManifest() = CharacterLayerManifest(
        id = "jedl_master_v1",
        version = 1,
        parts = listOf(
            CharacterLayerDef("head_base", CharacterLayerSlot.HEAD, "head", "head/base.png", 0.5f, 0.1f, 0.18f, 40),
            CharacterLayerDef("eyes_brown", CharacterLayerSlot.EYES, "head", "face/eyes.png", 0.5f, 0.5f, 0.09f, 60),
            CharacterLayerDef("hat_cap", CharacterLayerSlot.HAT_FRONT, "head", "hat/front.png", 0.5f, 0.8f, 0.20f, 70),
            CharacterLayerDef("shirt_white", CharacterLayerSlot.SHIRT, "torso", "clothes/shirt.png", 0.5f, 0.05f, 0.24f, 20)
        ),
        defaults = mapOf(
            CharacterLayerSlot.HEAD to "head_base",
            CharacterLayerSlot.EYES to "eyes_brown",
            CharacterLayerSlot.HAT_FRONT to "hat_cap",
            CharacterLayerSlot.SHIRT to "shirt_white"
        )
    )

    @Test fun acceptsPrototypeSlotsAsIndependentLayers() {
        assertTrue(CharacterLayerContract.validate(validManifest()).isValid)
    }

    @Test fun sortsResolvedPartsByZIndex() {
        val resolved = CharacterLayerContract.resolve(validManifest(), CharacterLayerAppearance())
        assertEquals(listOf("shirt_white", "head_base", "eyes_brown", "hat_cap"), resolved.map { it.id })
    }

    @Test fun appearanceReplacesOnlyItsSelectedSlot() {
        val manifest = validManifest().copy(
            parts = validManifest().parts + CharacterLayerDef(
                "eyes_green", CharacterLayerSlot.EYES, "head", "face/eyes_green.png",
                0.5f, 0.5f, 0.09f, 60
            )
        )
        val resolved = CharacterLayerContract.resolve(
            manifest,
            CharacterLayerAppearance(selectedParts = mapOf(CharacterLayerSlot.EYES to "eyes_green"))
        )
        assertEquals("eyes_green", resolved.single { it.slot == CharacterLayerSlot.EYES }.id)
        assertEquals("hat_cap", resolved.single { it.slot == CharacterLayerSlot.HAT_FRONT }.id)
    }

    @Test fun rejectsDuplicateIdsAndUnknownBones() {
        val manifest = validManifest().copy(
            parts = validManifest().parts + validManifest().parts.first().copy(
                slot = CharacterLayerSlot.EAR_L, boneId = "earSocket"
            )
        )
        val result = CharacterLayerContract.validate(manifest)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Duplicate") })
        assertTrue(result.errors.any { it.contains("Unknown bone") })
    }

    @Test fun rejectsTraversalPathsInvalidPivotsAndWidths() {
        val part = validManifest().parts.first().copy(
            file = "../outside.png", pivotX = Float.NaN, widthUnits = 0f
        )
        val result = CharacterLayerContract.validate(validManifest().copy(parts = listOf(part)))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("path") })
        assertTrue(result.errors.any { it.contains("Pivot") })
        assertTrue(result.errors.any { it.contains("widthUnits") })
    }

    @Test fun rejectsDefaultsPointingToWrongSlot() {
        val manifest = validManifest().copy(
            defaults = mapOf(CharacterLayerSlot.HEAD to "eyes_brown")
        )
        val result = CharacterLayerContract.validate(manifest)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("belongs to") })
    }

    @Test fun builtInStarterPackCoversTheHumanoidRig() {
        val manifest = CharacterLayerContract.starterManifest()
        assertTrue(CharacterLayerContract.validate(manifest).errors.joinToString()).also { }
        assertTrue(CharacterLayerContract.validate(manifest).isValid)
        val resolved = CharacterLayerContract.resolve(manifest, CharacterLayerAppearance())
        assertTrue(resolved.size >= 30)
        assertTrue(resolved.all { it.file.startsWith("builtin://") })
        assertTrue(resolved.all { it.boneId in CharacterLayerContract.supportedBones })
        val bones = resolved.map { it.boneId }.toSet()
        assertTrue(bones.containsAll(setOf(
            "torso", "head", "armUL", "foreArmL", "armUR",
            "foreArmR", "thighL", "shinL", "thighR", "shinR"
        )))
    }

    @Test fun starterAppearanceCanReplaceOnlyTheHatLayer() {
        val manifest = CharacterLayerContract.starterManifest()
        val alternate = manifest.copy(
            parts = manifest.parts + CharacterLayerDef(
                "cap_alt", CharacterLayerSlot.HAT_FRONT, "head", "builtin://cap_front",
                0.5f, 0f, 0.20f, 0.105f, null
            )
        )
        val resolved = CharacterLayerContract.resolve(
            alternate,
            CharacterLayerAppearance(selectedParts = mapOf(CharacterLayerSlot.HAT_FRONT to "cap_alt"))
        )
        assertEquals("cap_alt", resolved.single { it.slot == CharacterLayerSlot.HAT_FRONT }.id)
        assertEquals(
            CharacterLayerContract.resolve(manifest, CharacterLayerAppearance())
                .filter { it.slot != CharacterLayerSlot.HAT_FRONT }.map { it.id }.toSet(),
            resolved.filter { it.slot != CharacterLayerSlot.HAT_FRONT }.map { it.id }.toSet()
        )
    }

    @Test fun parserReadsIndependentPartsAndDefaults() {
        val json = """{
          "id":"jedl_master_v1","version":1,
          "parts":[{"id":"head","slot":"HEAD","boneId":"head","file":"head.png",
            "pivotX":0.5,"pivotY":0.1,"widthUnits":0.18,"zIndex":10}],
          "defaults":{"HEAD":"head"}
        }"""
        val parsed = CharacterLayerContract.parse(json)
        assertNotNull(parsed)
        assertTrue(CharacterLayerContract.validate(parsed!!).isValid)
        assertEquals("head", parsed.defaults[CharacterLayerSlot.HEAD])
    }
}
