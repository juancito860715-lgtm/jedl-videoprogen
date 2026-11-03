package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class PoseDetector(context: Context) {
    private val landmarker: PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .apply {
                baseOptions = BaseOptions.builder().setModelAssetPath("pose_landmarker.task").build()
                runningMode = RunningMode.IMAGE
                numPoses = 1
            }
            .build()
    )
    
    fun detectJoints(bitmap: Bitmap): List<PointF> {
        val result = landmarker.detect(BitmapImageBuilder(bitmap).build())
        val first = result.landmarks().firstOrNull() ?: return emptyList()
        return first.map { PointF(it.x() * bitmap.width, it.y() * bitmap.height) }
    }
}
