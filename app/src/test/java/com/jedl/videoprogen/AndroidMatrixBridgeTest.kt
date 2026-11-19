package com.jedl.videoprogen

import org.junit.Assert.assertArrayEquals
import org.junit.Test

/** Guards the exact 3x3 row-major values passed from the pure rig solver to Android Matrix.setValues(). */
class AndroidMatrixBridgeTest {
    @Test
    fun affineMatrixExportsAndroidNineValueLayout() {
        val affine = Affine2D(
            0f, -1f, 10f,
            1f, 0f, 20f
        )

        assertArrayEquals(
            floatArrayOf(0f, -1f, 10f, 1f, 0f, 20f, 0f, 0f, 1f),
            affine.toAndroidMatrixValues(),
            0.0001f
        )
    }
}