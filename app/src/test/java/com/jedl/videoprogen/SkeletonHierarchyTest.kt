package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Regression tests for the parent-first local transform order consumed by Skeleton.solve().
 *
 * Expected hierarchy:
 * world(child) = world(parent) * translate(child.attach) * rotate(child.localAngle)
 */
class SkeletonHierarchyTest {

    @Test
    fun rootRotationMustNotRotateItsWorldPosition() {
        val solved = SkeletalMath.resolveHierarchy(
            bones = listOf(
                SkeletalBoneDefinition(
                    name = "root",
                    parent = null,
                    length = 0f,
                    attachX = 0f,
                    attachY = 0f,
                    restDegrees = 90f
                )
            ),
            angles = emptyMap(),
            rootX = 10f,
            rootY = 20f
        )

        val rootOrigin = solved.getValue("root").transform.map(Vec2(0f, 0f))
        assertEquals(10f, rootOrigin.x, 0.0001f)
        assertEquals(20f, rootOrigin.y, 0.0001f)
    }

    @Test
    fun childAnchorMustFollowParentRotation() {
        val solved = SkeletalMath.resolveHierarchy(
            bones = listOf(
                SkeletalBoneDefinition("torso", null, 0f, 0f, 0f, 90f),
                SkeletalBoneDefinition("head", "torso", 0f, 5f, 0f, 0f)
            ),
            angles = emptyMap(),
            rootX = 10f,
            rootY = 20f
        )

        val headOrigin = solved.getValue("head").joint
        assertEquals(10f, headOrigin.x, 0.0001f)
        assertEquals(25f, headOrigin.y, 0.0001f)
        assertEquals(90f, solved.getValue("head").worldDegrees, 0.0001f)
    }

    @Test
    fun grandchildAnchorInheritsAllAncestorTransforms() {
        val solved = SkeletalMath.resolveHierarchy(
            bones = listOf(
                SkeletalBoneDefinition("torso", null, 0f, 0f, 0f, 90f),
                SkeletalBoneDefinition("arm", "torso", 0f, 5f, 0f, 90f),
                SkeletalBoneDefinition("hand", "arm", 0f, 3f, 0f, 0f)
            ),
            angles = emptyMap(),
            rootX = 10f,
            rootY = 20f
        )

        val handOrigin = solved.getValue("hand").joint
        assertEquals(7f, handOrigin.x, 0.0001f)
        assertEquals(25f, handOrigin.y, 0.0001f)
        assertEquals(180f, solved.getValue("hand").worldDegrees, 0.0001f)
    }

    @Test
    fun childCannotReferenceAParentThatHasNotBeenSolved() {
        assertThrows(IllegalArgumentException::class.java) {
            SkeletalMath.resolveHierarchy(
                bones = listOf(
                    SkeletalBoneDefinition("hand", "arm", 1f, 0f, 0f, 0f),
                    SkeletalBoneDefinition("arm", null, 1f, 0f, 0f, 0f)
                ),
                angles = emptyMap(),
                rootX = 0f,
                rootY = 0f
            )
        }
    }

    @Test
    fun duplicateBoneNamesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SkeletalMath.resolveHierarchy(
                bones = listOf(
                    SkeletalBoneDefinition("root", null, 1f, 0f, 0f, 0f),
                    SkeletalBoneDefinition("root", null, 1f, 0f, 0f, 0f)
                ),
                angles = emptyMap()
            )
        }
    }
}
