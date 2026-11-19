package com.jedl.videoprogen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCommonContractTest {

    @Test
    fun parsesVersionedImageManifestAndNormalizedPivot() {
        val json = """
            {
              "schemaVersion": 1,
              "id": "hero_head",
              "kind": "IMAGE",
              "path": "assets/characters/hero/head.png",
              "width": 512,
              "height": 512,
              "pivot": { "x": 0.5, "y": 0.92 }
            }
        """.trimIndent()

        val manifest = EngineResourceManifest.fromJson(json)

        assertEquals("hero_head", manifest.id)
        assertEquals(EngineResourceKind.IMAGE, manifest.kind)
        assertEquals(0.5f, manifest.pivot!!.x, 0f)
        assertEquals(0.92f, manifest.pivot!!.y, 0f)
        assertNull(EngineResourceManifest.validate(manifest))
    }

    @Test
    fun rejectsUnsupportedManifestVersionAndUnknownKind() {
        val unsupported = """{"schemaVersion":99,"id":"asset_1","kind":"IMAGE","path":"assets/a.png","width":64,"height":64}"""
        val unknownKind = """{"schemaVersion":1,"id":"asset_1","kind":"MAGIC","path":"assets/a.png"}"""

        assertFalse(runCatching { EngineResourceManifest.fromJson(unsupported) }.isSuccess)
        assertFalse(runCatching { EngineResourceManifest.fromJson(unknownKind) }.isSuccess)
    }

    @Test
    fun rejectsUnsafePathsAndInvalidDimensionsOrPivot() {
        val unsafe = EngineResourceManifest(
            id = "asset_1",
            kind = EngineResourceKind.IMAGE,
            path = "../private.png",
            width = 64,
            height = 64
        )
        val invalidSize = unsafe.copy(path = "assets/a.png", width = 0)
        val invalidPivot = unsafe.copy(path = "assets/a.png", pivot = NormalizedPivot(1.1f, 0.5f))

        assertTrue(EngineResourceManifest.validate(unsafe) != null)
        assertTrue(EngineResourceManifest.validate(invalidSize) != null)
        assertTrue(EngineResourceManifest.validate(invalidPivot) != null)
    }

    @Test
    fun permitsAudioManifestWithoutImageDimensions() {
        val audio = EngineResourceManifest(
            id = "voice_intro",
            kind = EngineResourceKind.AUDIO,
            path = "assets/audio/intro.wav"
        )
        assertNull(EngineResourceManifest.validate(audio))
    }

    @Test
    fun timebaseUsesDeterministicFrameIndicesAndCeilingFrameCount() {
        val clock = SceneTimebase(framesPerSecond = 30, durationMillis = 15_000L)

        assertEquals(450L, clock.frameCount)
        assertEquals(0L, clock.presentationTimeUs(0L))
        assertEquals(33_333L, clock.presentationTimeUs(1L))
        assertEquals(15_000_000L, clock.presentationTimeUs(450L))
        assertEquals(30L, clock.frameIndexAt(1_000_000L))
        assertEquals(clock.frameCount, clock.frameIndexAt(Long.MAX_VALUE))
    }

    @Test
    fun timebaseRoundsUpPartialFinalFrameAndRejectsInvalidInputs() {
        val clock = SceneTimebase(framesPerSecond = 24, durationMillis = 1_001L)

        assertEquals(25L, clock.frameCount)
        assertFalse(runCatching { SceneTimebase(0, 1_000L) }.isSuccess)
        assertFalse(runCatching { SceneTimebase(30, 0L) }.isSuccess)
        assertFalse(runCatching { SceneTimebase(61, 1_000L) }.isSuccess)
    }
}
