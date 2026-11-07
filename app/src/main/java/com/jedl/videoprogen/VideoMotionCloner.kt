package com.jedl.videoprogen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

class VideoMotionCloner(context: Context) {
    private val landmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            // OJO: el asset se llama "pose_landmarker.task" en el APK (asi lo
            // guarda el paso de descarga del CI, modelo lite 5.5 MB).
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker.task").build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .build()
    )

    fun extractMotion(frames: List<Bitmap>): List<List<PointF>> {
        // LIMITACION DOCUMENTADA (Redmi Note 10, 4 GB RAM):
        // solo se analizan los primeros 150 frames (~5 segundos a 30 fps).
        // Un video mas largo se TRUNCA aqui para que la app no se congele
        // ni falle por falta de memoria procesandolo completo.
        val limitedFrames = frames.take(150)

        val sequence = mutableListOf<List<PointF>>()
        for ((index, frame) in limitedFrames.withIndex()) {
            val mpImage = BitmapImageBuilder(frame).build()
            val timestampMs = index * 33L // ~30fps
            val result = landmarker.detectForVideo(mpImage, timestampMs)
            val points = result.landmarks().firstOrNull()?.map { PointF(it.x(), it.y()) } ?: emptyList()
            sequence.add(points)
        }
        return sequence
    }

    // v0.5.0: libera el landmarker de MediaPipe (~30 MB de RAM nativa).
    // Sin esto el proceso del clonado dejaba la IA cargada para siempre.
    fun close() {
        landmarker.close()
    }
}
