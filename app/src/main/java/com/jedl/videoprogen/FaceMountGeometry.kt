package com.jedl.videoprogen

data class NormalizedFaceRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
data class BoneLocalFaceRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

object FaceMountGeometry {
    val defaultFaceRect = NormalizedFaceRect(0.18f, 0.20f, 0.82f, 0.86f)

    fun mapToBoneLocal(
        headLeft: Float, headTop: Float, headWidth: Float, headHeight: Float,
        faceRect: NormalizedFaceRect = defaultFaceRect
    ): BoneLocalFaceRect? {
        val values = listOf(headLeft, headTop, headWidth, headHeight,
            faceRect.left, faceRect.top, faceRect.right, faceRect.bottom)
        if (values.any { !it.isFinite() } || headWidth <= 0f || headHeight <= 0f) return null
        if (faceRect.left !in 0f..1f || faceRect.top !in 0f..1f ||
            faceRect.right !in 0f..1f || faceRect.bottom !in 0f..1f ||
            faceRect.right <= faceRect.left || faceRect.bottom <= faceRect.top) return null
        return BoneLocalFaceRect(
            headLeft + headWidth * faceRect.left,
            headTop + headHeight * faceRect.top,
            headLeft + headWidth * faceRect.right,
            headTop + headHeight * faceRect.bottom
        )
    }
}
