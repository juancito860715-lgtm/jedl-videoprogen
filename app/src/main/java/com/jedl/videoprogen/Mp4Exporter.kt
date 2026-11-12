package com.jedl.videoprogen

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/**
 * Exporta cuadros ARGB a H.264/MP4 usando el encoder hardware/software disponible.
 *
 * El formato de entrada elegido es YUV420 semi-planar (NV12). Si el dispositivo
 * no anuncia ese formato, se falla explícitamente en vez de enviar bytes con
 * un layout que el codec no soporta.
 */
class Mp4Exporter(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val durationSeconds: Float
) {
    fun export(
        outPath: String,
        onProgress: (Int) -> Unit,
        renderFrame: (Float) -> Bitmap
    ): Boolean {
        if (outPath.isBlank() ||
            width < 2 || height < 2 ||
            width > 1920 || height > 1920 ||
            width % 2 != 0 || height % 2 != 0 ||
            fps !in 1..60 ||
            !durationSeconds.isFinite() || durationSeconds <= 0f
        ) return false

        val totalFrames = (fps * durationSeconds).toInt()
        if (totalFrames <= 0) return false

        val output = File(outPath)
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var success = false

        try {
            output.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) error("No se pudo crear el directorio de salida")
            }

            val codecInfo = findNv12Encoder(width, height, fps)
                ?: error("No hay un encoder H.264 compatible con YUV420 semi-planar")

            val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                )
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createByCodecName(codecInfo.name)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val codec = encoder
            val mp4 = muxer
            val info = MediaCodec.BufferInfo()
            var trackIndex = -1
            var formatSeen = false

            // Drena todos los buffers disponibles. Devuelve true al recibir EOS.
            fun drain(endOfStream: Boolean): Boolean {
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!formatSeen) { "El encoder cambió el formato de salida más de una vez" }
                            trackIndex = mp4.addTrack(codec.outputFormat)
                            mp4.start()
                            muxerStarted = true
                            formatSeen = true
                        }
                        outputIndex >= 0 -> {
                            try {
                                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                    info.size = 0
                                }
                                if (info.size > 0) {
                                    check(muxerStarted && trackIndex >= 0) {
                                        "El encoder produjo datos antes del formato de salida"
                                    }
                                    val buffer = checkNotNull(codec.getOutputBuffer(outputIndex)) {
                                        "Buffer de salida nulo"
                                    }
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    mp4.writeSampleData(trackIndex, buffer, info)
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    return true
                                }
                            } finally {
                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                        else -> return false
                    }
                    if (!endOfStream && info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return true
                    }
                }
            }

            for (frameIndex in 0 until totalFrames) {
                val bitmap = renderFrame(frameIndex.toFloat() / totalFrames.toFloat())
                val yuv = try {
                    bitmapToNV12(bitmap, width, height)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

                val deadline = System.nanoTime() + INPUT_TIMEOUT_NS
                var queued = false
                while (!queued && System.nanoTime() < deadline) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(inputIndex)) {
                            "Buffer de entrada nulo"
                        }
                        input.clear()
                        check(yuv.size <= input.remaining()) {
                            "El cuadro YUV excede la capacidad del encoder"
                        }
                        input.put(yuv)
                        val ptsUs = frameIndex * 1_000_000L / fps
                        codec.queueInputBuffer(inputIndex, 0, yuv.size, ptsUs, 0)
                        queued = true
                    } else {
                        drain(false)
                    }
                }
                check(queued) { "Tiempo agotado esperando buffer de entrada" }
                onProgress(((frameIndex + 1) * 100) / totalFrames)
                drain(false)
            }

            val eosDeadline = System.nanoTime() + EOS_TIMEOUT_NS
            var eosQueued = false
            while (!eosQueued && System.nanoTime() < eosDeadline) {
                val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (inputIndex >= 0) {
                    codec.queueInputBuffer(
                        inputIndex, 0, 0,
                        totalFrames * 1_000_000L / fps,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    eosQueued = true
                } else {
                    drain(false)
                }
            }
            check(eosQueued) { "Tiempo agotado enviando fin de flujo al encoder" }

            var eosReceived = false
            while (!eosReceived && System.nanoTime() < eosDeadline) {
                eosReceived = drain(true)
            }
            check(eosReceived) { "Tiempo agotado esperando fin de flujo del encoder" }
            check(muxerStarted) { "El encoder no produjo un formato MP4 válido" }

            // Solo se declara éxito si el muxer finaliza correctamente.
            mp4.stop()
            muxerStarted = false
            success = output.exists() && output.length() > 0L
        } catch (_: Exception) {
            success = false
        } finally {
            try { encoder?.stop() } catch (_: Exception) { }
            try { encoder?.release() } catch (_: Exception) { }
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Exception) { success = false }
            }
            try { muxer?.release() } catch (_: Exception) { success = false }
            if (!success) {
                try { output.delete() } catch (_: Exception) { }
            }
        }
        return success
    }

    private fun findNv12Encoder(
        width: Int,
        height: Int,
        fps: Int
    ): MediaCodecInfo? {
        val codecs = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        return codecs.firstOrNull { codec ->
            if (!codec.isEncoder || codec.supportedTypes.none { it.equals("video/avc", true) }) {
                false
            } else {
                try {
                    val caps = codec.getCapabilitiesForType("video/avc")
                    val supportsNv12 = caps.colorFormats.contains(
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    )
                    val videoCaps = caps.videoCapabilities
                    supportsNv12 && videoCaps.areSizeAndRateSupported(
                        width, height, fps.toDouble()
                    )
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private fun bitmapToNV12(bitmap: Bitmap, w: Int, h: Int): ByteArray {
        val source = if (bitmap.width == w && bitmap.height == h) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        try {
            val argb = IntArray(w * h)
            source.getPixels(argb, 0, w, 0, 0, w, h)
            val yuv = ByteArray(w * h * 3 / 2)

            var yIndex = 0
            for (i in argb.indices) {
                val c = argb[i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                yuv[yIndex++] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
                    .coerceIn(16, 235).toByte()
            }

            var uvIndex = w * h
            var row = 0
            while (row < h) {
                var col = 0
                while (col < w) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (dy in 0..1) {
                        for (dx in 0..1) {
                            val c = argb[(row + dy) * w + col + dx]
                            red += (c shr 16) and 0xff
                            green += (c shr 8) and 0xff
                            blue += c and 0xff
                        }
                    }
                    val r = red / 4
                    val g = green / 4
                    val b = blue / 4
                    yuv[uvIndex++] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                        .coerceIn(16, 240).toByte()
                    yuv[uvIndex++] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                        .coerceIn(16, 240).toByte()
                    col += 2
                }
                row += 2
            }
            return yuv
        } finally {
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val INPUT_TIMEOUT_NS = 10_000_000_000L
        const val EOS_TIMEOUT_NS = 15_000_000_000L
    }
}
