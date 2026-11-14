package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterAppearanceCatalogTest {

    private fun validCatalog() = CharacterCatalog(
        id = "jedl_master_v1",
        version = 1,
        parts = listOf(
            CharacterPartDefinition("skin_medium", CharacterSlot.SKIN, "characters/jedl/skin/medium.png", 10),
            CharacterPartDefinition("eyes_brown", CharacterSlot.EYES, "characters/jedl/face/eyes_brown.png", 30),
            CharacterPartDefinition("hair_short", CharacterSlot.HAIR, "characters/jedl/hair/short.png", 40),
            CharacterPartDefinition("cap_orange", CharacterSlot.HAT, "characters/jedl/hats/orange.png", 50),
            CharacterPartDefinition("hoodie_blue", CharacterSlot.TOP, "characters/jedl/clothes/hoodie_blue.png", 20),
            CharacterPartDefinition("pants_navy", CharacterSlot.BOTTOM, "characters/jedl/clothes/pants_navy.png", 15),
            CharacterPartDefinition("sneakers_orange", CharacterSlot.SHOES, "characters/jedl/shoes/orange.png", 5)
        ),
        defaults = mapOf(
            CharacterSlot.SKIN to "skin_medium",
            CharacterSlot.EYES to "eyes_brown",
            CharacterSlot.HAIR to "hair_short",
            CharacterSlot.HAT to "cap_orange",
            CharacterSlot.TOP to "hoodie_blue",
            CharacterSlot.BOTTOM to "pants_navy",
            CharacterSlot.SHOES to "sneakers_orange"
        )
    )

    @Test
    fun acceptsCatalogWithUniquePartsAndValidDefaults() {
        val result = CharacterCatalogValidator.validate(validCatalog())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun rejectsDuplicatePartIds() {
        val catalog = validCatalog().copy(
            parts = validCatalog().parts + validCatalog().parts.first().copy(slot = CharacterSlot.ACCESSORY)
        )
        val result = CharacterCatalogValidator.validate(catalog)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("duplicate", ignoreCase = true) })
    }

    @Test
    fun rejectsAssetPathsThatEscapeTheCharacterAssetDirectory() {
        val catalog = validCatalog().copy(
            parts = validCatalog().parts.map {
                if (it.id == "eyes_brown") it.copy(assetPath = "../private/eyes.png") else it
            }
        )
        val result = CharacterCatalogValidator.validate(catalog)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("path", ignoreCase = true) })
    }

    @Test
    fun rejectsDefaultPartAssignedToTheWrongSlot() {
        val catalog = validCatalog().copy(
            defaults = validCatalog().defaults + (CharacterSlot.HAT to "eyes_brown")
        )
        val result = CharacterCatalogValidator.validate(catalog)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("slot", ignoreCase = true) })
    }

    @Test
    fun appearanceCanSelectOnePartPerSlotAndIndependentColorChannels() {
        val appearance = CharacterAppearanceDocument(
            catalogId = "jedl_master_v1",
            selectedParts = mapOf(
                CharacterSlot.SKIN to "skin_medium",
                CharacterSlot.EYES to "eyes_brown",
                CharacterSlot.HAIR to "hair_short",
                CharacterSlot.HAT to "cap_orange"
            ),
            colors = mapOf(
                "skin" to 0xFFC68B59.toInt(),
                "hat.crown" to 0xFFFF7900.toInt(),
                "hat.panel" to 0xFFF7F7F7.toInt(),
                "shoes.sole" to 0xFF202A38.toInt()
            )
        )
        assertEquals("cap_orange", appearance.selectedParts[CharacterSlot.HAT])
        assertEquals(0xFFFF7900.toInt(), appearance.colors["hat.crown"])
        assertEquals(0xFFF7F7F7.toInt(), appearance.colors["hat.panel"])
        assertTrue(CharacterAppearanceValidator.validate(appearance, validCatalog()).isEmpty())
    }

    @Test
    fun rejectsAppearancePartNotPresentInCatalog() {
        val appearance = CharacterAppearanceDocument(
            catalogId = "jedl_master_v1",
            selectedParts = mapOf(CharacterSlot.HAT to "hat_that_does_not_exist"),
            colors = emptyMap()
        )
        val errors = CharacterAppearanceValidator.validate(appearance, validCatalog())
        assertTrue(errors.any { it.contains("unknown", ignoreCase = true) })
    }
}
