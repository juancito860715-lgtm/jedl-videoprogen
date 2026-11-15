package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterLayerRigBindingTest {
    @Test fun starterPackBindsEveryPartToAnExistingRigBone() {
        val rigNames = PuppetRig.HUMANOID.bones.map { it.name }.toSet()
        val layers = CharacterLayerContract.resolve(
            CharacterLayerContract.starterManifest(),
            CharacterLayerAppearance()
        )
        assertEquals(35, layers.size)
        assertTrue(layers.all { it.boneId in rigNames })
    }

    @Test fun allStarterArtworkIdsHaveAConcretePainterImplementation() {
        val manifest = CharacterLayerContract.starterManifest()
        assertTrue(manifest.parts.all {
            CharacterPartPainter.supportedPartIds.contains(it.file.removePrefix("builtin://"))
        })
    }

    @Test fun forearmFollowsUpperArmRotationThroughExistingHierarchy() {
        val bind = PuppetRig.HUMANOID.solve(emptyMap(), 0f, 0f)
        val animated = PuppetRig.HUMANOID.solve(mapOf("armUL" to 35f), 0f, 0f)
        val bindTip = bind.getValue("foreArmL").tip
        val animatedTip = animated.getValue("foreArmL").tip
        assertNotEquals(bindTip.x, animatedTip.x, 0.0001f)
        assertNotEquals(bindTip.y, animatedTip.y, 0.0001f)
    }

    @Test fun legPartsFollowHipAndKneeHierarchy() {
        val bind = PuppetRig.HUMANOID.solve(emptyMap(), 0f, 0f)
        val animated = PuppetRig.HUMANOID.solve(
            mapOf("thighR" to 28f, "shinR" to -18f), 0f, 0f
        )
        assertNotEquals(bind.getValue("shinR").tip.x, animated.getValue("shinR").tip.x, 0.0001f)
    }
}
