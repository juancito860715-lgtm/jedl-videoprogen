package com.jedl.videoprogen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CharacterArtworkManifestTest {
    @Test fun everyRigPartUsesItsOwnTransparentRuntimeLayer() {
        val file = listOf(
            File("src/main/assets/characters/jedl_master_v1/manifest.json"),
            File("app/src/main/assets/characters/jedl_master_v1/manifest.json")
        ).firstOrNull { it.isFile }
        assertTrue("Character manifest must exist", file != null)
        val manifestFile = requireNotNull(file)
        val assetRoot = requireNotNull(manifestFile.parentFile)
        val root = JSONObject(manifestFile.readText())
        val parts = root.getJSONArray("parts")
        val ids = mutableSetOf<String>()
        val paths = mutableSetOf<String>()
        val supportedBones = setOf(
            "torso", "head", "armUL", "foreArmL", "armUR", "foreArmR",
            "thighL", "shinL", "thighR", "shinR"
        )
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            val id = part.getString("id")
            val path = part.getString("file")
            assertTrue("Duplicate part id: $id", ids.add(id))
            assertTrue("Each rig part must have a distinct PNG: $path", paths.add(path))
            assertEquals("layers/$id.png", path)
            val png = File(assetRoot, path)
            assertTrue("Generated PNG missing for $id: $path", png.isFile)
            assertTrue("Generated PNG is empty for $id: $path", png.length() > 100L)
            assertTrue("Unknown rig bone for $id", supportedBones.contains(part.getString("boneId")))
            assertTrue("Pivot X must be normalized for $id",
                part.getDouble("pivotX").isFinite() && part.getDouble("pivotX") in 0.0..1.0)
            assertTrue("Pivot Y must be normalized for $id",
                part.getDouble("pivotY").isFinite() && part.getDouble("pivotY") in 0.0..1.0)
        }
        assertEquals("Every manifest part must map to one unique image", parts.length(), paths.size)
        assertTrue("Expected the complete starter rig art set", parts.length() >= 40)
        assertFalse("No procedural builtin placeholders may remain", (0 until parts.length()).any {
            parts.getJSONObject(it).getString("file").startsWith("builtin://")
        })
    }
}
