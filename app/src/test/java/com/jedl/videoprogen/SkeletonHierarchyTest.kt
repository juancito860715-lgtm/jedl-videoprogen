package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Regression tests for the world/local transform order used by Skeleton.solve().
 *
 * The expected hierarchy is:
 * world(child) = world(parent) * translate(child.attach) * rotate(child.localAngle)
 */
class SkeletonHierarchyTest {

    @Test
    fun rootRotationMustNotRotateItsWorldPosition() {
        val solved = SkeletalHierarchySolver.solve(
            nodes = listOf(
                SkeletalBoneNode(
                    name = "root",
                    parent = null,
                    attachX = 0f,
                    attachY = 0f,
                    restDegrees = 90f
                )
            ),
            rootX = 10f,
            rootY = 20f
        )

        val rootOrigin = solved.getValue("root").matrix.map(Vec2(0f, 0f))
        assertEquals(10f, rootOrigin.x, 0.0001f)
        assertEquals(20f, rootOrigin.y, 0.0001f)
    }

    @Test
    fun childAnchorMustFollowParentRotation() {
        val solved = SkeletalHierarchySolver.solve(
            nodes = listOf(
                SkeletalBoneNode("torso", null, 0f, 0f, 90f),
                SkeletalBoneNode("head", "torso", 5f, 0f, 0f)
            ),
            rootX = 10f,
            rootY = 20f
        )

        val headOrigin = solved.getValue("head").matrix.map(Vec2(0f, 0f))
        assertEquals(10f, headOrigin.x, 0.0001f)
        assertEquals(25f, headOrigin.y, 0.0001f)
        assertEquals(90f, solved.getValue("head").worldDegrees, 0.0001f)
    }

    @Test
    fun grandchildAnchorInheritsAllAncestorTransforms() {
        val solved = SkeletalHierarchySolver.solve(
            nodes = listOf(
                SkeletalBoneNode("torso", null, 0f, 0f, 90f),
                SkeletalBoneNode("arm", "torso", 5f, 0f, 90f),
                SkeletalBoneNode("hand", "arm", 3f, 0f, 0f)
            ),
            rootX = 10f,
            rootY = 20f
        )

        val handOrigin = solved.getValue("hand").matrix.map(Vec2(0f, 0f))
        assertEquals(7f, handOrigin.x, 0.0001f)
        assertEquals(25f, handOrigin.y, 0.0001f)
        assertEquals(180f, solved.getValue("hand").worldDegrees, 0.0001f)
    }

    @Test
    fun childCannotReferenceAParentThatHasNotBeenSolved() {
        assertThrows(IllegalArgumentException::class.java) {
            SkeletalHierarchySolver.solve(
                nodes = listOf(
                    SkeletalBoneNode("hand", "arm", 1f, 0f, 0f),
                    SkeletalBoneNode("arm", null, 0f, 0f, 0f)
                ),
                rootX = 0f,
                rootY = 0f
            )
        }
    }
}
