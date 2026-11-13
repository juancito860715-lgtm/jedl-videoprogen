package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectAnimationSamplerTest {

    private fun clip(
        target: String = "00000000-0000-0000-0000-000000000001",
        loop: Boolean = false
    ) = AnimationClipDocument(
        name = "Clip de prueba",
        targetLayerId = target,
        durationMillis = 1_000L,
        loop = loop,
        keyframes = listOf(
            AnimationKeyframeDocument(
                timeMillis = 0L,
                boneAngles = mapOf("armUR" to 0f, "torso" to 10f),
                rootBob = 0f,
                rootSway = -0.02f
            ),
            AnimationKeyframeDocument(
                timeMillis = 1_000L,
                boneAngles = mapOf("armUR" to 100f, "head" to 20f),
                rootBob = 0.04f,
                rootSway = 0.02f
            )
        )
    )

    @Test
    fun interpolatesAnglesAndRootMotionWithSmoothstep() {
        val pose = ProjectAnimationSampler.sample(clip(), 500L)

        assertEquals(50f, pose.angles["armUR"]!!, 0.0001f)
        assertEquals(5f, pose.angles["torso"]!!, 0.0001f)
        assertEquals(10f, pose.angles["head"]!!, 0.0001f)
        assertEquals(0.02f, pose.bob, 0.0001f)
        assertEquals(0f, pose.sway, 0.0001f)
    }

    @Test
    fun nonLoopingClipHoldsLastPoseAfterDuration() {
        val pose = ProjectAnimationSampler.sample(clip(), 5_000L)

        assertEquals(100f, pose.angles["armUR"]!!, 0.0001f)
        assertEquals(0.04f, pose.bob, 0.0001f)
    }

    @Test
    fun loopingClipReturnsToFirstPoseAtDurationBoundary() {
        val pose = ProjectAnimationSampler.sample(clip(loop = true), 1_000L)

        assertEquals(0f, pose.angles["armUR"]!!, 0.0001f)
        assertEquals(-0.02f, pose.sway, 0.0001f)
    }

    @Test
    fun eachCharacterLayerResolvesItsOwnClip() {
        val firstLayer = LayerDocument(
            id = "00000000-0000-0000-0000-000000000001",
            type = LayerType.CHARACTER
        )
        val secondLayer = LayerDocument(
            id = "00000000-0000-0000-0000-000000000002",
            type = LayerType.CHARACTER
        )
        val firstClip = clip(target = firstLayer.id).copy(
            keyframes = listOf(AnimationKeyframeDocument(0L, mapOf("armUR" to -90f)))
        )
        val secondClip = clip(target = secondLayer.id).copy(
            keyframes = listOf(AnimationKeyframeDocument(0L, mapOf("armUR" to 45f)))
        )
        val scene = SceneDocument(
            layers = listOf(firstLayer, secondLayer),
            animationClips = listOf(firstClip, secondClip)
        )

        val firstPose = ProjectAnimationSampler.poseForLayer(scene, firstLayer, 0L)
        val secondPose = ProjectAnimationSampler.poseForLayer(scene, secondLayer, 0L)

        assertEquals(-90f, firstPose!!.angles["armUR"]!!, 0.0001f)
        assertEquals(45f, secondPose!!.angles["armUR"]!!, 0.0001f)
        assertNull(ProjectAnimationSampler.poseForLayer(
            scene,
            LayerDocument(id = "00000000-0000-0000-0000-000000000003", type = LayerType.CHARACTER),
            0L
        ))
    }

    @Test
    fun animationTimeStartsRelativeToCharacterLayerStart() {
        val layer = LayerDocument(
            id = "00000000-0000-0000-0000-000000000004",
            type = LayerType.CHARACTER,
            startMillis = 500L
        )
        val scene = SceneDocument(
            layers = listOf(layer),
            animationClips = listOf(clip(target = layer.id))
        )

        val atLayerStart = ProjectAnimationSampler.poseForLayer(scene, layer, 500L)
        val halfwayThroughClip = ProjectAnimationSampler.poseForLayer(scene, layer, 1_000L)

        assertEquals(0f, atLayerStart!!.angles["armUR"]!!, 0.0001f)
        assertEquals(50f, halfwayThroughClip!!.angles["armUR"]!!, 0.0001f)
    }

    @Test
    fun firstClipForACharacterIsTheActiveClipUntilTimelineSequencingExists() {
        val layer = LayerDocument(
            id = "00000000-0000-0000-0000-000000000005",
            type = LayerType.CHARACTER
        )
        val first = clip(target = layer.id).copy(
            keyframes = listOf(AnimationKeyframeDocument(0L, mapOf("armUR" to -30f)))
        )
        val second = clip(target = layer.id).copy(
            keyframes = listOf(AnimationKeyframeDocument(0L, mapOf("armUR" to 80f)))
        )
        val scene = SceneDocument(layers = listOf(layer), animationClips = listOf(first, second))

        val pose = ProjectAnimationSampler.poseForLayer(scene, layer, 0L)

        assertEquals(-30f, pose!!.angles["armUR"]!!, 0.0001f)
    }
}
