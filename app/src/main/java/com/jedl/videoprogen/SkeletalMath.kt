package com.jedl.videoprogen

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable vector in scene-independent rig units. */
data class Vec2(val x: Float, val y: Float)

/** Local transform for a bone or attachment. Rotation is clockwise in screen coordinates. */
data class SkeletalTransform2D(
    val x: Float = 0f,
    val y: Float = 0f,
    val rotationDegrees: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

/** 2D affine matrix stored as [m00 m01 m02; m10 m11 m12; 0 0 1]. */
data class Affine2D(
    val m00: Float, val m01: Float, val m02: Float,
    val m10: Float, val m11: Float, val m12: Float
) {
    fun map(point: Vec2): Vec2 = Vec2(
        m00 * point.x + m01 * point.y + m02,
        m10 * point.x + m11 * point.y + m12
    )
}

data class TwoBoneSolution(val joint: Vec2, val end: Vec2, val reachedTarget: Boolean)

/** Local joint angles and the actual endpoint after applying joint limits. */
data class TwoBoneRigSolution(
    val upperAngleDegrees: Float,
    val lowerAngleDegrees: Float,
    val joint: Vec2,
    val end: Vec2,
    val reachedTarget: Boolean,
    val bendDirection: Float,
    val targetError: Float
)

/**
 * Deterministic, allocation-light skeletal math shared by rig tests and runtime.
 * All distances are expressed in the character's normalized rig units.
 */
object SkeletalMath {
    private const val EPSILON = 1e-5f

    fun matrix(transform: SkeletalTransform2D): Affine2D {
        requireFinite(transform.x, transform.y, transform.rotationDegrees, transform.scaleX, transform.scaleY)
        val radians = transform.rotationDegrees * (PI.toFloat() / 180f)
        val c = cos(radians)
        val s = sin(radians)
        return Affine2D(
            c * transform.scaleX, -s * transform.scaleY, transform.x,
            s * transform.scaleX,  c * transform.scaleY, transform.y
        )
    }

    /** World = parent * local; child translations therefore inherit parent rotation and scale. */
    fun compose(parent: SkeletalTransform2D, local: SkeletalTransform2D): Affine2D =
        multiply(matrix(parent), matrix(local))

    fun multiply(a: Affine2D, b: Affine2D): Affine2D = Affine2D(
        a.m00 * b.m00 + a.m01 * b.m10,
        a.m00 * b.m01 + a.m01 * b.m11,
        a.m00 * b.m02 + a.m01 * b.m12 + a.m02,
        a.m10 * b.m00 + a.m11 * b.m10,
        a.m10 * b.m01 + a.m11 * b.m11,
        a.m10 * b.m02 + a.m11 * b.m12 + a.m12
    )

    /** Interpolates on the shortest angular arc, avoiding a 340-degree spin at +/-180. */
    fun lerpAngleDegrees(from: Float, to: Float, progress: Float): Float {
        requireFinite(from, to, progress)
        val t = progress.coerceIn(0f, 1f)
        val delta = wrapDegrees(to - from)
        return wrapDegrees(from + delta * t)
    }

    /**
     * Analytic two-segment IK using the law of cosines.
     * The endpoint is clamped to the reachable annulus; bendDirection selects elbow/knee side.
     */
    fun solveTwoBone(
        root: Vec2,
        target: Vec2,
        upperLength: Float,
        lowerLength: Float,
        bendDirection: Float = 1f
    ): TwoBoneSolution {
        requireFinite(root.x, root.y, target.x, target.y, upperLength, lowerLength, bendDirection)
        require(upperLength > EPSILON && lowerLength > EPSILON) { "Bone lengths must be positive" }
        require(bendDirection != 0f) { "bendDirection must be non-zero" }

        val dx = target.x - root.x
        val dy = target.y - root.y
        val rawDistance = sqrt(dx * dx + dy * dy)
        val maxReach = upperLength + lowerLength
        val minReach = kotlin.math.abs(upperLength - lowerLength)
        val distance = rawDistance.coerceIn(minReach + EPSILON, maxReach - EPSILON)
        val ux: Float
        val uy: Float
        if (rawDistance <= EPSILON) {
            ux = 1f
            uy = 0f
        } else {
            ux = dx / rawDistance
            uy = dy / rawDistance
        }

        val along = (upperLength * upperLength - lowerLength * lowerLength + distance * distance) / (2f * distance)
        val heightSquared = (upperLength * upperLength - along * along).coerceAtLeast(0f)
        val height = sqrt(heightSquared) * if (bendDirection > 0f) 1f else -1f
        val joint = Vec2(
            root.x + ux * along - uy * height,
            root.y + uy * along + ux * height
        )
        val end = Vec2(root.x + ux * distance, root.y + uy * distance)
        return TwoBoneSolution(joint, end, rawDistance <= maxReach && rawDistance >= minReach)
    }

    /**
     * Converts an analytic two-bone solution into local rig angles.
     *
     * The solver tests both elbow/knee sides, keeps the side of the current pose
     * when possible, applies both joint limits, then chooses the candidate whose
     * resulting endpoint is closest to the requested target.
     *
     * Angles use the rig convention: 0° points down, 90° right, 180° up.
     */
    fun solveTwoBoneAngles(
        root: Vec2,
        target: Vec2,
        upperLength: Float,
        lowerLength: Float,
        currentJoint: Vec2,
        currentEnd: Vec2,
        parentWorldDegrees: Float,
        upperRestDegrees: Float,
        lowerRestDegrees: Float,
        upperMinDegrees: Float,
        upperMaxDegrees: Float,
        lowerMinDegrees: Float,
        lowerMaxDegrees: Float,
        defaultBendDirection: Float
    ): TwoBoneRigSolution {
        requireFinite(
            root.x, root.y, target.x, target.y,
            upperLength, lowerLength,
            currentJoint.x, currentJoint.y, currentEnd.x, currentEnd.y,
            parentWorldDegrees, upperRestDegrees, lowerRestDegrees,
            upperMinDegrees, upperMaxDegrees, lowerMinDegrees, lowerMaxDegrees,
            defaultBendDirection
        )
        require(upperMinDegrees <= upperMaxDegrees) { "Invalid upper joint limits" }
        require(lowerMinDegrees <= lowerMaxDegrees) { "Invalid lower joint limits" }
        require(defaultBendDirection != 0f) { "defaultBendDirection must be non-zero" }

        val currentDx = currentEnd.x - root.x
        val currentDy = currentEnd.y - root.y
        val currentJointDx = currentJoint.x - root.x
        val currentJointDy = currentJoint.y - root.y
        val currentSide = currentDx * currentJointDy - currentDy * currentJointDx
        val preferredDirection = when {
            kotlin.math.abs(currentSide) > EPSILON -> if (currentSide > 0f) 1f else -1f
            defaultBendDirection > 0f -> 1f
            else -> -1f
        }

        fun candidate(direction: Float): TwoBoneRigSolution {
            val geometry = solveTwoBone(root, target, upperLength, lowerLength, direction)
            val desiredUpperWorld = angleDegrees(root, geometry.joint)
            val upperLocal = wrapDegrees(
                desiredUpperWorld - parentWorldDegrees - upperRestDegrees
            ).coerceIn(upperMinDegrees, upperMaxDegrees)
            val actualUpperWorld = wrapDegrees(parentWorldDegrees + upperRestDegrees + upperLocal)
            val actualJoint = offset(root, actualUpperWorld, upperLength)

            // If the upper joint was clamped, aim the lower segment from its
            // actual new joint position rather than from the unclamped solution.
            val desiredLowerWorld = angleDegrees(actualJoint, target)
            val lowerLocal = wrapDegrees(
                desiredLowerWorld - actualUpperWorld - lowerRestDegrees
            ).coerceIn(lowerMinDegrees, lowerMaxDegrees)
            val actualLowerWorld = wrapDegrees(actualUpperWorld + lowerRestDegrees + lowerLocal)
            val actualEnd = offset(actualJoint, actualLowerWorld, lowerLength)

            val errorX = actualEnd.x - target.x
            val errorY = actualEnd.y - target.y
            val error = sqrt(errorX * errorX + errorY * errorY)
            val tolerance = maxOf(1e-4f, (upperLength + lowerLength) * 1e-3f)
            return TwoBoneRigSolution(
                upperAngleDegrees = upperLocal,
                lowerAngleDegrees = lowerLocal,
                joint = actualJoint,
                end = actualEnd,
                reachedTarget = geometry.reachedTarget && error <= tolerance,
                bendDirection = direction,
                targetError = error
            )
        }

        val preferred = candidate(preferredDirection)
        val alternate = candidate(-preferredDirection)
        return if (alternate.targetError + EPSILON < preferred.targetError) alternate else preferred
    }

    private fun angleDegrees(from: Vec2, to: Vec2): Float =
        Math.toDegrees(kotlin.math.atan2(
            (to.x - from.x).toDouble(),
            (to.y - from.y).toDouble()
        )).toFloat()

    private fun offset(origin: Vec2, angleDegrees: Float, length: Float): Vec2 {
        val radians = angleDegrees * (PI.toFloat() / 180f)
        return Vec2(
            origin.x + sin(radians) * length,
            origin.y + cos(radians) * length
        )
    }

    fun wrapDegrees(value: Float): Float {
        require(value.isFinite()) { "Angle must be finite" }
        var result = value % 360f
        if (result > 180f) result -= 360f
        if (result <= -180f) result += 360f
        return result
    }

    private fun requireFinite(vararg values: Float) {
        require(values.all { it.isFinite() }) { "Skeletal values must be finite" }
    }
}
