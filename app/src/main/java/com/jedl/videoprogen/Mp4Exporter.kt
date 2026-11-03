package com.jedl.videoprogen

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

class Mp4Exporter(private val width: Int, private val height: Int, private val fps: Int, private val durationSeconds: Float) {
    fun export(outPath: String, onProgress: (Int) -> Unit, renderFrame: (Float) -> Bitmap): Boolean {
        val totalFrames = (fps * durationSeconds).toInt()
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType("video/avc")
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        File(outPath).parentFile?.mkdirs()
        val muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            for (frame in 0 until totalFrames) {
                val bitmap = renderFrame(frame / totalFrames.toFloat())
                val yuv = bitmapToNV12(bitmap, width, height)
                bitmap.recycle()

                val inIdx = encoder.dequeueInputBuffer(10000)
                if (inIdx >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inIdx)!!
                    inputBuffer.clear(); inputBuffer.put(yuv)
                    encoder.queueInputBuffer(inIdx, 0, yuv.size, frame * 1_000_000L / fps, 0)
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(encoder.outputFormat); muxer.start(); muxerStarted = true
                    } else if (outIdx >= 0) {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bufferInfo.size = 0
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset); outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                    } else break
                }
                onProgress(((frame + 1) * 100) / totalFrames)
            }
            return true
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            encoder.release()
            if (muxerStarted) try { muxer.stop() } catch (_: Exception) {}
            muxer.release()
        }
    }

    private fun bitmapToNV12(bitmap: Bitmap, w: Int, h: Int): ByteArray {
        val src = if (bitmap.width == w && bitmap.height == h) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val argb = IntArray(w * h); src.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIdx = 0
        for (i in 0 until w * h) {
            val c = argb[i]; val r = (c shr 16) and 0xff; val g = (c shr 8) and 0xff; val b = c and 0xff
            yuv[yIdx++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(16, 235).toByte()
        }
        var uvIdx = w * h; var j = 0
        while (j < h) {
            var i = 0
            while (i < w) {
                var rS = 0; var gS = 0; var bS = 0
                for (dj in 0..1) for (di in 0..1) {
                    val c = argb[(j + dj).coerceAtMost(h - 1) * w + (i + di).coerceAtMost(w - 1)]
                    rS += (c shr 16) and 0xff; gS += (c shr 8) and 0xff; bS += c and 0xff
                }
                val r = rS / 4; val g = gS / 4; val b = bS / 4
                yuv[uvIdx++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                yuv[uvIdx++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(16, 240).toByte()
                i += 2
            }
            j += 2
        }
        if (src !== bitmap) src.recycle()
        return yuv
    }
}
