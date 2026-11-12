package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProjectAnimationMapperTest {
    @Test
    fun convertsExistingMotionTemplateToPersistentClipAndBack() {
        val source = MotionTemplate(
            "Wave",
            listOf(
                PoseFrame(0f, Pose(mapOf("armUR" to -120f), 0.01f, -0.02f)),
                PoseFrame(1f, Pose(mapOf("armUR" to -80f), 0f, 0.01f))
            ),
            loop = true
        )

        val clip = ProjectAnimationMapper.fromMotionTemplate(
            source,
            targetLayerId = "00000000-0000-0000-0000-000000000001",
            durationMillis = 2_000L
        )
        assertEquals(0L, clip.keyframes.first().timeMillis)
        assertEquals(2_000L, clip.keyframes.last().timeMillis)

        val restored = ProjectAnimationMapper.toMotionTemplate(clip)
        assertEquals("Wave", restored.name)
        assertTrue(restored.loop)
        assertEquals(-80f, restored.frames.last().pose.angles["armUR"]!!)
        assertEquals(-0.02f, restored.frames.first().pose.sway, 0.0001f)
    }

    @Test
    fun rejectsKeyframesThatCollapseToSameMillisecond() {
        val source = MotionTemplate(
            "Too dense",
            listOf(
                PoseFrame(0.1000f, Pose(mapOf("armUR" to 0f), bob = 0f, sway = 0f)),
                PoseFrame(0.1001f, Pose(mapOf("armUR" to 10f), bob = 0f, sway = 0f))
            ),
            loop = false
        )
        try {
            ProjectAnimationMapper.fromMotionTemplate(
                source,
                targetLayerId = "00000000-0000-0000-0000-000000000001",
                durationMillis = 1_000L
            )
            fail("Expected millisecond collision to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("coinciden"))
        }
    }

    @Test
    fun rejectsNonFiniteRootMotion() {
        val source = MotionTemplate(
            "Invalid",
            listOf(PoseFrame(0f, Pose(emptyMap(), Float.NaN, 0f))),
            loop = false
        )
        try {
            ProjectAnimationMapper.fromMotionTemplate(
                source,
                targetLayerId = "00000000-0000-0000-0000-000000000001",
                durationMillis = 1_000L
            )
            fail("Expected invalid root motion to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("desplazamientos"))
        }
    }
}
