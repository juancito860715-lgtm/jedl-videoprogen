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
        assertEquals(1f, result.bendDirection, 0f)
        assertTrue(result.lowerAngleDegrees < 0f)
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
            currentJoint = Vec2(-0.88f, 4.92f),
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

        assertEquals(1f, result.bendDirection, 0f)
        assertTrue(result.reachedTarget)
        assertTrue(result.lowerAngleDegrees < 0f)
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
        assertEquals(
            result.targetError,
            hypot((result.end.x - 8f).toDouble(), result.end.y.toDouble()).toFloat(),
            0.001f
        )
        assertTrue(result.targetError.isFinite())
    }

    @Test
    fun rigAngleSolverMatchesAndroidMatrixScreenRotationConvention() {
        val root = Vec2(2f, 3f)
        val target = Vec2(6f, 9f)
        val result = SkeletalMath.solveTwoBoneAngles(
            root = root,
            target = target,
            upperLength = 5f,
            lowerLength = 5f,
            currentJoint = Vec2(root.x, root.y - 5f),
            currentEnd = Vec2(root.x, root.y - 10f),
            parentWorldDegrees = 35f,
            upperRestDegrees = 180f,
            lowerRestDegrees = 0f,
            upperMinDegrees = -180f,
            upperMaxDegrees = 180f,
            lowerMinDegrees = -150f,
            lowerMaxDegrees = 5f,
            defaultBendDirection = 1f
        )

        val upperWorld = SkeletalMath.wrapDegrees(
            35f + 180f + result.upperAngleDegrees
        )
        val lowerWorld = SkeletalMath.wrapDegrees(
            upperWorld + result.lowerAngleDegrees
        )
        val upperVector = SkeletalMath.matrix(
            SkeletalTransform2D(rotationDegrees = upperWorld)
        ).map(Vec2(0f, 5f))
        val lowerVector = SkeletalMath.matrix(
            SkeletalTransform2D(rotationDegrees = lowerWorld)
        ).map(Vec2(0f, 5f))

        assertEquals(root.x + upperVector.x, result.joint.x, 0.001f)
        assertEquals(root.y + upperVector.y, result.joint.y, 0.001f)
        assertEquals(result.joint.x + lowerVector.x, result.end.x, 0.001f)
        assertEquals(result.joint.y + lowerVector.y, result.end.y, 0.001f)
        assertTrue(result.reachedTarget)
        assertEquals(target.x, result.end.x, 0.001f)
        assertEquals(target.y, result.end.y, 0.001f)
    }

    @Test
    fun hierarchyChildAttachmentFollowsRotatedParentTip() {
        val bones = listOf(
            SkeletalBoneDefinition(
                name = "root",
                parent = null,
                length = 10f,
                attachX = 0f,
                attachY = 0f,
                restDegrees = 90f
            ),
            SkeletalBoneDefinition(
                name = "child",
                parent = "root",
                length = 5f,
                attachX = 0f,
                attachY = 10f,
                restDegrees = 0f
            )
        )

        val solved = SkeletalMath.resolveHierarchy(
            bones = bones,
            angles = emptyMap(),
            rootX = 3f,
            rootY = 4f
        )
        val root = solved.getValue("root")
        val child = solved.getValue("child")

        assertEquals(3f, root.joint.x, 0.001f)
        assertEquals(4f, root.joint.y, 0.001f)
        assertEquals(-7f, root.tip.x, 0.001f)
        assertEquals(4f, root.tip.y, 0.001f)
        assertEquals(root.tip.x, child.joint.x, 0.001f)
        assertEquals(root.tip.y, child.joint.y, 0.001f)
        assertEquals(-12f, child.tip.x, 0.001f)
        assertEquals(4f, child.tip.y, 0.001f)
        assertEquals(90f, child.worldDegrees, 0.001f)
    }

    @Test
    fun screenAngleUsesAndroidClockwiseMatrixConvention() {
        assertEquals(
            90f,
            SkeletalMath.angleDegrees(Vec2(0f, 0f), Vec2(-1f, 0f)),
            0.001f
        )
        assertEquals(
            -90f,
            SkeletalMath.angleDegrees(Vec2(0f, 0f), Vec2(1f, 0f)),
            0.001f
        )
    }
}
