package com.jedl.videoprogen

import org.junit.Assert.*
import org.junit.Test

class FaceMountGeometryTest {
    @Test fun mapsNormalizedFaceRectIntoHeadBoneLocalSpace() {
        val result = FaceMountGeometry.mapToBoneLocal(-0.09f, 0f, 0.18f, 0.20f)!!
        assertEquals(-0.0576f, result.left, 0.00001f)
        assertEquals(0.04f, result.top, 0.00001f)
        assertEquals(0.0576f, result.right, 0.00001f)
        assertEquals(0.172f, result.bottom, 0.00001f)
    }

    @Test fun rejectsInvalidOrNonFiniteFaceRect() {
        assertNull(FaceMountGeometry.mapToBoneLocal(0f, 0f, 0f, 1f))
        assertNull(FaceMountGeometry.mapToBoneLocal(0f, 0f, 1f, 1f,
            NormalizedFaceRect(0.8f, 0.2f, 0.1f, 0.9f)))
        assertNull(FaceMountGeometry.mapToBoneLocal(Float.NaN, 0f, 1f, 1f))
    }
}
