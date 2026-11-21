package com.jedl.videoprogen

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Yuv420FrameConverterTest {

    @Test
    fun convertsSolidRedFrameToNv12WithExpectedLimitedRangeSamples() {
        val argb = IntArray(4) { 0xFFFF0000.toInt() }
        val output = ByteBuffer.allocate(6)

        val written = Yuv420FrameConverter.write(
            argb = argb,
            width = 2,
            height = 2,
            stride = 2,
            sliceHeight = 2,
            layout = Yuv420Layout.SEMIPLANAR_UV,
            target = output
        )

        assertEquals(6, written)
        repeat(4) { assertEquals(82, output.get(it).toInt() and 0xFF) }
        assertEquals(90, output.get(4).toInt() and 0xFF)
        assertEquals(240, output.get(5).toInt() and 0xFF)
    }

    @Test
    fun writesPlanarI420ChromaInSeparateUThenVPlanes() {
        val argb = IntArray(4) { 0xFFFF0000.toInt() }
        val output = ByteBuffer.allocate(6)

        Yuv420FrameConverter.write(
            argb = argb,
            width = 2,
            height = 2,
            stride = 2,
            sliceHeight = 2,
            layout = Yuv420Layout.PLANAR_I420,
            target = output
        )

        assertEquals(90, output.get(4).toInt() and 0xFF)
        assertEquals(240, output.get(5).toInt() and 0xFF)
    }

    @Test
    fun respectsPaddedStrideAndSliceHeightForNv12() {
        val argb = IntArray(8) { 0xFFFF0000.toInt() }
        val output = ByteBuffer.allocate(36)

        val written = Yuv420FrameConverter.write(
            argb = argb,
            width = 4,
            height = 2,
            stride = 6,
            sliceHeight = 4,
            layout = Yuv420Layout.SEMIPLANAR_UV,
            target = output
        )

        assertEquals(36, written)
        for (x in 0 until 4) {
            assertEquals(82, output.get(x).toInt() and 0xFF)
            assertEquals(82, output.get(6 + x).toInt() and 0xFF)
        }
        assertEquals(16, output.get(4).toInt() and 0xFF)
        assertEquals(16, output.get(10).toInt() and 0xFF)
        assertEquals(90, output.get(24).toInt() and 0xFF)
        assertEquals(240, output.get(25).toInt() and 0xFF)
        assertEquals(90, output.get(26).toInt() and 0xFF)
        assertEquals(240, output.get(27).toInt() and 0xFF)
        assertEquals(128, output.get(28).toInt() and 0xFF)
    }

    @Test
    fun respectsPaddedStrideForPlanarI420() {
        val argb = IntArray(8) { 0xFFFF0000.toInt() }
        val output = ByteBuffer.allocate(36)

        Yuv420FrameConverter.write(
            argb = argb,
            width = 4,
            height = 2,
            stride = 6,
            sliceHeight = 4,
            layout = Yuv420Layout.PLANAR_I420,
            target = output
        )

        // Y plane = 6 * 4 = 24 bytes; each chroma plane = 3 * 2 = 6 bytes.
        assertEquals(90, output.get(24).toInt() and 0xFF)
        assertEquals(90, output.get(25).toInt() and 0xFF)
        assertEquals(240, output.get(30).toInt() and 0xFF)
        assertEquals(240, output.get(31).toInt() and 0xFF)
    }

    @Test
    fun rejectsOddDimensionsInvalidPaddingAndInsufficientBuffers() {
        val argb = IntArray(16) { 0xFF000000.toInt() }

        assertFalse(runCatching {
            Yuv420FrameConverter.requiredBufferSize(3, 2, 4, 2)
        }.isSuccess)
        assertFalse(runCatching {
            Yuv420FrameConverter.requiredBufferSize(4, 2, 2, 2)
        }.isSuccess)
        assertFalse(runCatching {
            Yuv420FrameConverter.requiredBufferSize(4, 2, 4, 1)
        }.isSuccess)
        assertFalse(runCatching {
            Yuv420FrameConverter.write(
                argb, 4, 4, 4, 4, Yuv420Layout.SEMIPLANAR_UV, ByteBuffer.allocate(8)
            )
        }.isSuccess)
        assertFalse(runCatching {
            Yuv420FrameConverter.write(
                IntArray(3), 2, 2, 2, 2, Yuv420Layout.SEMIPLANAR_UV, ByteBuffer.allocate(6)
            )
        }.isSuccess)
    }
}
