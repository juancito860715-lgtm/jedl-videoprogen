package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

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
        assertEquals(6f, hypot(result.end.x.toDouble(), result.end.y.toDouble()).toFloat(), 0.0001f)
        assertEquals(5f, hypot(result.joint.x.toDouble(), result.joint.y.toDouble()).toFloat(), 0.0001f)
        assertEquals(
            5f,
            hypot(
                (result.end.x - result.joint.x).toDouble(),
                (result.end.y - result.joint.y).toDouble()
            ).toFloat(),
            0.0001f
        )
        assertTrue(result.reachedTarget)
    }

    @Test
    fun twoBoneIkClampsUnreachableTargetToMaximumReach() {
        val result = SkeletalMath.solveTwoBone(
            root = Vec2(0f, 0f),
            target = Vec2(20f, 0f),
            upperLength = 3f,
            lowerLength = 4f
        )
        assertEquals(7f, hypot(result.end.x.toDouble(), result.end.y.toDouble()).toFloat(), 0.0001f)
        assertFalse(result.reachedTarget)
    }

    @Test
    fun twoBoneIkClampsTargetInsideMinimumReach() {
        val result = SkeletalMath.solveTwoBone(
            root = Vec2(0f, 0f),
            target = Vec2(0f, 0f),
            upperLength = 5f,
            lowerLength = 2f
        )
        assertEquals(3f, hypot(result.end.x.toDouble(), result.end.y.toDouble()).toFloat(), 0.001f)
        assertFalse(result.reachedTarget)
    }

    @Test
    fun zeroLengthAndNonFiniteInputsAreRejected() {
        var zeroLengthRejected = false
        try {
            SkeletalMath.solveTwoBone(Vec2(0f, 0f), Vec2(1f, 1f), 0f, 2f)
        } catch (_: IllegalArgumentException) {
            zeroLengthRejected = true
        }
        assertTrue(zeroLengthRejected)

        var nonFiniteRejected = false
        try {
            SkeletalMath.lerpAngleDegrees(Float.NaN, 10f, 0.5f)
        } catch (_: IllegalArgumentException) {
            nonFiniteRejected = true
        }
        assertTrue(nonFiniteRejected)
    }

    @Test
    fun rigAngleSolverReachesTargetAndHonorsNegativeLowerJointLimit() {
        val result = SkeletalMath.solveTwoBoneAngles(
            root = Vec2(0f, 0f),
            target = Vec2(4f, 6f),
            upperLength = 5f,
            lowerLength = 5f,
            currentJoint = Vec2(0f, -5f),
            currentEnd = Vec2(0f, -10f),
            parentWorldDegrees = 35f,
            upperRestDegrees = 180f,
            lowerRestDegrees = 0f,
            upperMinDegrees = -180f,
            upperMaxDegrees = 180f,
            lowerMinDegrees = -150f,
            lowerMaxDegrees = 5f,
            defaultBendDirection = -1f
        )

        assertTrue(result.reachedTarget)
        assertEquals(-1f, result.bendDirection, 0f)
        assertEquals(4f, result.end.x, 0.001f)
        assertEquals(6f, result.end.y, 0.001f)
        assertEquals(5f, hypot(result.joint.x.toDouble(), result.joint.y.toDouble()).toFloat(), 0.001f)
        assertEquals(
            5f,
            hypot(
                (result.end.x - result.joint.x).toDouble(),
                (result.end.y - result.joint.y).toDouble()
            ).toFloat(),
            0.001f
        )
        assertTrue(result.lowerAngleDegrees in -150f..5f)
    }

    @Test
    fun rigAngleSolverKeepsExistingBendSideWhenCurrentPoseIsAlreadyBent() {
        val result = SkeletalMath.solveTwoBoneAngles(
            root = Vec2(0f, 0f),
            target = Vec2(4f, 6f),
            upperLength = 5f,
            lowerLength = 5f,
            currentJoint = Vec2(4.88f, 1.08f),
            currentEnd = Vec2(4f, 6f),
            parentWorldDegrees = 0f,
            upperRestDegrees = 180f,
            lowerRestDegrees = 0f,
            upperMinDegrees = -180f,
            upperMaxDegrees = 180f,
            lowerMinDegrees = -150f,
            lowerMaxDegrees = 5f,
            defaultBendDirection = 1f
        )

        assertEquals(-1f, result.bendDirection, 0f)
        assertTrue(result.reachedTarget)
        assertTrue(result.lowerAngleDegrees in -150f..5f)
    }

    @Test
    fun rigAngleSolverAppliesBothJointLimitsAndReportsRemainingError() {
        val result = SkeletalMath.solveTwoBoneAngles(
            root = Vec2(0f, 0f),
            target = Vec2(8f, 0f),
            upperLength = 3f,
            lowerLength = 4f,
            currentJoint = Vec2(0f, -3f),
            currentEnd = Vec2(0f, -7f),
            parentWorldDegrees = 0f,
            upperRestDegrees = 180f,
            lowerRestDegrees = 0f,
            upperMinDegrees = -60f,
            upperMaxDegrees = 60f,
            lowerMinDegrees = -140f,
            lowerMaxDegrees = 5f,
            defaultBendDirection = -1f
        )

        assertFalse(result.reachedTarget)
        assertTrue(result.upperAngleDegrees in -60f..60f)
        assertTrue(result.lowerAngleDegrees in -140f..5f)
        assertTrue(result.targetError > 0f)
        assertTrue(result.targetError.isFinite())
    }
}
