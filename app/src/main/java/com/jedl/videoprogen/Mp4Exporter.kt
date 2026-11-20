package com.jedl.videoprogen

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import kotlin.math.roundToLong

class Mp4Exporter(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val durationMillis: Long
) {
    private val timebase = SceneTimebase(fps, durationMillis)

    /**
     * Compatibility overload for existing callers that still supply seconds.
     * New project exports should pass the exact persisted duration in millis.
     */
    constructor(
        width: Int,
        height: Int,
        fps: Int,
        durationSeconds: Float
    ) : this(width, height, fps, durationSecondsToMillis(durationSeconds))

    init {
        require(width >= 64 && height >= 64 && width % 2 == 0 && height % 2 == 0) {
            "Dimensiones de exportación no válidas"
        }
    }

    fun export(
        outPath: String,
        onProgress: (Int) -> Unit,
        renderFrame: (Float) -> Bitmap
    ): Boolean {
        // SceneTimebase caps duration to one hour and FPS to 1..60, so this
        // conversion is safely within Int range for the current exporter.
        val totalFrames = timebase.frameCount.toInt()
        val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
            )
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
        val timeoutUs = 10_000L

        try {
            for (frame in 0 until totalFrames) {
                val frameIndex = frame.toLong()
                val presentationTimeUs = timebase.presentationTimeUs(frameIndex)
                val renderProgress = timebase.normalizedProgressAt(frameIndex)
                val bitmap = renderFrame(renderProgress)
                val yuv = bitmapToNV12(bitmap, width, height)
                bitmap.recycle()

                var inIdx = encoder.dequeueInputBuffer(timeoutUs)
                while (inIdx < 0) {
                    inIdx = encoder.dequeueInputBuffer(timeoutUs)
                }

                val inputBuffer = encoder.getInputBuffer(inIdx)!!
                inputBuffer.clear()
                inputBuffer.put(yuv)
                encoder.queueInputBuffer(inIdx, 0, yuv.size, presentationTimeUs, 0)

                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outIdx >= 0) {
                        val outBuf = encoder.getOutputBuffer(outIdx)!!
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (bufferInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                    } else {
                        break
                    }
                }
                onProgress(((frame + 1) * 100) / totalFrames)
            }

            var eosIdx = encoder.dequeueInputBuffer(timeoutUs)
            while (eosIdx < 0) {
                eosIdx = encoder.dequeueInputBuffer(timeoutUs)
            }
            encoder.queueInputBuffer(
                eosIdx,
                0,
                0,
                timebase.durationUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )

            var eosReceived = false
            while (!eosReceived) {
                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outIdx >= 0) {
                    val outBuf = encoder.getOutputBuffer(outIdx)!!
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0 && muxerStarted) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReceived = true
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                }
            }
            return true
        } finally {
            try {
                encoder.stop()
            } catch (_: Exception) {
            }
            encoder.release()
            if (muxerStarted) {
                try {
                    muxer.stop()
                } catch (_: Exception) {
                }
            }
            muxer.release()
        }
    }

    private fun bitmapToNV12(bitmap: Bitmap, w: Int, h: Int): ByteArray {
        val src = if (bitmap.width == w && bitmap.height == h) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        val argb = IntArray(w * h)
        src.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIdx = 0
        for (i in 0 until w * h) {
            val c = argb[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            yuv[yIdx++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                .coerceIn(16, 235).toByte()
        }
        var uvIdx = w * h
        var j = 0
        while (j < h) {
            var i = 0
            while (i < w) {
                var rS = 0
                var gS = 0
                var bS = 0
                for (dj in 0..1) for (di in 0..1) {
                    val c = argb[(j + dj).coerceAtMost(h - 1) * w + (i + di).coerceAtMost(w - 1)]
                    rS += (c shr 16) and 0xff
                    gS += (c shr 8) and 0xff
                    bS += c and 0xff
                }
                val r = rS / 4
                val g = gS / 4
                val b = bS / 4
                yuv[uvIdx++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                    .coerceIn(16, 240).toByte()
                yuv[uvIdx++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                    .coerceIn(16, 240).toByte()
                i += 2
            }
            j += 2
        }
        if (src !== bitmap) src.recycle()
        return yuv
    }

    private companion object {
        fun durationSecondsToMillis(seconds: Float): Long {
            require(seconds.isFinite() && seconds > 0f) {
                "Duración de exportación no válida"
            }
            val millis = (seconds.toDouble() * 1_000.0).roundToLong()
            require(millis in 1L..3_600_000L) {
                "Duración de exportación fuera de rango"
            }
            return millis
        }
    }
}
