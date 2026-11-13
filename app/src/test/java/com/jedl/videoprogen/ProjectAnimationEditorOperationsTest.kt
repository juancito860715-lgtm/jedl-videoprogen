package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectAnimationEditorOperationsTest {
    private fun clip() = AnimationClipDocument(
        name = "Prueba",
        targetLayerId = "00000000-0000-0000-0000-000000000001",
        durationMillis = 2_000L,
        keyframes = listOf(
            AnimationKeyframeDocument(0L, mapOf("armUR" to -30f, "torso" to 2f), rootBob = 0.01f),
            AnimationKeyframeDocument(2_000L, mapOf("armUR" to -90f))
        )
    )

    @Test
    fun createsAndMovesPoseWhilePreservingOtherJointAngles() {
        val updated = ProjectAnimationEditorOperations.upsertPose(
            clip(), selectedTimeMillis = 0L, newTimeMillis = 500L,
            boneName = "armUR", angleDegrees = -120f
        )
        assertEquals(listOf(500L, 2_000L), updated.keyframes.map { it.timeMillis })
        assertEquals(-120f, updated.keyframes.first().boneAngles["armUR"])
        assertEquals(2f, updated.keyframes.first().boneAngles["torso"])
        assertEquals(0.01f, updated.keyframes.first().rootBob)
    }

    @Test
    fun rejectsDuplicateTimesAndOutOfRangeAngles() {
        try {
            ProjectAnimationEditorOperations.upsertPose(clip(), 0L, 2_000L, "armUR", 10f)
            throw AssertionError("Expected duplicate time to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Ya existe"))
        }
        try {
            ProjectAnimationEditorOperations.upsertPose(clip(), 0L, 500L, "armUR", Float.NaN)
            throw AssertionError("Expected invalid angle to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Ángulo"))
        }
    }

    @Test
    fun deletesPoseButKeepsAtLeastOne() {
        val updated = ProjectAnimationEditorOperations.deletePose(clip(), 0L)
        assertEquals(listOf(2_000L), updated.keyframes.map { it.timeMillis })
        try {
            ProjectAnimationEditorOperations.deletePose(updated, 2_000L)
            throw AssertionError("Expected final keyframe protection")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("al menos una"))
        }
    }
}
