package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SkeletalMathTest {
    @Test
    fun parentAndLocalTransformsComposeInHierarchyOrder() {
        val parent = SkeletalTransform2D(x = 10f, y = 20f, rotationDegrees = 90f)
        val child = SkeletalTransform2D(x = 5f, y = 0f)
        val world = SkeletalMath.compose(parent, child)
        val origin = world.map(Vec2(0f, 0f))
        assertEquals(10f, origin.x, 0.0001f)
        assertEquals(25f, origin.y, 0.0001f)
    }

    @Test
    fun angleInterpolationUsesShortestArcAcrossWrapBoundary() {
        assertEquals(180f, SkeletalMath.lerpAngleDegrees(170f, -170f, 0.5f), 0.0001f)
    }

    @Test
    fun twoBoneIkReachesTargetWhenTargetIsReachable() {
        val result = SkeletalMath.solveTwoBone(
            root = Vec2(0f, 0f),
            target = Vec2(6f, 0f),
            upperLength = 5f,
            lowerLength = 5f
        )
        assertEquals(6f, sqrt(result.end.x * result.end.x + result.end.y * result.end.y), 0.0001f)
        assertEquals(5f, sqrt(result.joint.x * result.joint.x + result.joint.y * result.joint.y), 0.0001f)
        assertEquals(5f, sqrt((result.end.x - result.joint.x) * (result.end.x - result.joint.x) + (result.end.y - result.joint.y) * (result.end.y - result.joint.y)), 0.0001f)
    }

    @Test
    fun twoBoneIkClampsUnreachableTargetToMaximumReach() {
        val result = SkeletalMath.solveTwoBone(
            root = Vec2(0f, 0f),
            target = Vec2(20f, 0f),
            upperLength = 3f,
            lowerLength = 4f
        )
        assertEquals(7f, hypot(result.end.x, result.end.y), 0.0001f)
    }

    @Test
    fun zeroLengthAndNonFiniteInputsAreRejected() {
        var threw = false
        try {
            SkeletalMath.solveTwoBone(Vec2(0f, 0f), Vec2(1f, 1f), 0f, 2f)
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
