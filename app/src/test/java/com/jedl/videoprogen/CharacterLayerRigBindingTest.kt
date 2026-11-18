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
        assertEquals(39, layers.size)
        assertTrue(layers.all { it.boneId in rigNames })
    }

    @Test fun allStarterArtworkIdsHaveAConcretePainterImplementation() {
        val manifest = CharacterLayerContract.starterManifest()
        assertTrue(manifest.parts.all {
            CharacterPartPainter.supportedPartIds.contains(it.file.removePrefix("builtin://"))
        })
    }

    private fun solveRigTips(angles: Map<String, Float>): Map<String, Vec2> {
        val bones = PuppetRig.HUMANOID.bones
        val definitions = bones.map { bone ->
            SkeletalBoneDefinition(
                name = bone.name,
                parent = bone.parent,
                length = bone.length,
                attachX = bone.attachX,
                attachY = bone.attachY,
                restDegrees = bone.restDeg
            )
        }
        val resolved = SkeletalMath.resolveHierarchy(definitions, angles)
        return bones.associate { bone ->
            bone.name to resolved.getValue(bone.name).tip
        }
    }

    @Test fun forearmFollowsUpperArmRotationThroughExistingHierarchy() {
        val bind = solveRigTips(emptyMap()).getValue("foreArmL")
        val animated = solveRigTips(mapOf("armUL" to 35f)).getValue("foreArmL")
        assertNotEquals(bind.x, animated.x, 0.0001f)
        assertNotEquals(bind.y, animated.y, 0.0001f)
    }

    @Test fun legPartsFollowHipAndKneeHierarchy() {
        val bind = solveRigTips(emptyMap()).getValue("shinR")
        val animated = solveRigTips(mapOf("thighR" to 28f, "shinR" to -18f)).getValue("shinR")
        assertNotEquals(bind.x, animated.x, 0.0001f)
    }
}
