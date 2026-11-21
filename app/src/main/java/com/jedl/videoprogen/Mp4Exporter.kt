package com.jedl.videoprogen

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import kotlin.math.roundToLong

class Mp4Exporter(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val durationMillis: Long
) {
    private val timebase = SceneTimebase(fps, durationMillis)

    /**
     * Compatibility overload for callers that still supply seconds.
     * Project exports should pass the persisted millisecond duration.
     */
    constructor(
        width: Int,
        height: Int,
        fps: Int,
        durationSeconds: Float
    ) : this(width, height, fps, durationSecondsToMillis(durationSeconds))

    init {
        require(
            width >= 64 && height >= 64 &&
                width <= 1_920 && height <= 1_920 &&
                width % 2 == 0 && height % 2 == 0 &&
                width.toLong() * height.toLong() <= 2_073_600L
        ) { "Dimensiones de exportación no válidas para el perfil móvil" }
    }

    /**
     * The renderFrame callback transfers ownership of its Bitmap to this
     * exporter. The bitmap is read once and recycled on every exit path.
     */
    fun export(
        outPath: String,
        onProgress: (Int) -> Unit,
        renderFrame: (Float) -> Bitmap
    ): Boolean {
        val totalFrames = timebase.frameCount.toInt()
        val outputFile = File(outPath)
        outputFile.parentFile?.let { parent ->
            if (!parent.exists() && !parent.mkdirs()) {
                throw IllegalStateException("No se pudo crear la carpeta temporal de exportación")
            }
        }
        if (outputFile.exists() && !outputFile.delete()) {
            throw IllegalStateException("No se pudo reemplazar el archivo temporal anterior")
        }

        var encoder: MediaCodec? = null
        var encoderStarted = false
        var muxer: MediaMuxer? = null
        val muxerState = MuxerState()
        var completed = false

        try {
            val started = createStartedEncoder()
            val activeEncoder = started.codec
            encoder = activeEncoder
            encoderStarted = true

            val activeMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = activeMuxer
            val bufferInfo = MediaCodec.BufferInfo()
            val argb = IntArray(width * height)

            for (frame in 0 until totalFrames) {
                val frameIndex = frame.toLong()
                val bitmap = renderFrame(timebase.normalizedProgressAt(frameIndex))
                try {
                    copyBitmapToArgb(bitmap, argb)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

                val inputIndex = awaitInputBuffer(
                    activeEncoder,
                    activeMuxer,
                    muxerState,
                    bufferInfo
                )
                queueFrame(
                    encoder = activeEncoder,
                    started = started,
                    inputIndex = inputIndex,
                    argb = argb,
                    presentationTimeUs = timebase.presentationTimeUs(frameIndex)
                )
                drainAvailableOutput(activeEncoder, activeMuxer, muxerState, bufferInfo)
                onProgress(((frame + 1) * 99) / totalFrames)
            }

            val eosInputIndex = awaitInputBuffer(
                activeEncoder,
                activeMuxer,
                muxerState,
                bufferInfo
            )
            activeEncoder.queueInputBuffer(
                eosInputIndex,
                0,
                0,
                timebase.durationUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
            drainUntilEndOfStream(activeEncoder, activeMuxer, muxerState, bufferInfo)

            check(muxerState.started && muxerState.trackIndex >= 0) {
                "El codificador no produjo un formato de salida H.264"
            }

            try {
                activeMuxer.stop()
                muxerState.started = false
            } catch (error: Exception) {
                muxerState.started = false
                throw IllegalStateException("No se pudo finalizar el archivo MP4", error)
            }

            if (!outputFile.isFile || outputFile.length() <= 0L) {
                throw IllegalStateException("El codificador no generó un MP4 válido")
            }
            onProgress(100)
            completed = true
            return true
        } finally {
            if (encoderStarted) {
                try {
                    encoder?.stop()
                } catch (_: Exception) {
                    // A failed export must still release the codec.
                }
            }
            try {
                encoder?.release()
            } catch (_: Exception) {
            }

            if (muxerState.started) {
                try {
                    muxer?.stop()
                } catch (_: Exception) {
                } finally {
                    muxerState.started = false
                }
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }

            if (!completed && outputFile.exists()) {
                outputFile.delete()
            }
        }
    }

    private fun createStartedEncoder(): StartedEncoder {
        val candidates = findEncoderCandidates()
        if (candidates.isEmpty()) {
            throw IllegalStateException(
                "Este dispositivo no anuncia un codificador H.264 con entrada YUV420 compatible"
            )
        }

        var lastFailure: Exception? = null
        for (candidate in candidates) {
            var codec: MediaCodec? = null
            var started = false
            try {
                codec = MediaCodec.createByCodecName(candidate.codecName)
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    width,
                    height
                ).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, candidate.colorFormat)
                    setInteger(MediaFormat.KEY_BIT_RATE, recommendedBitRate())
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                codec.start()
                started = true

                val inputFormat = codec.inputFormat
                val stride = inputFormat.readPositiveOrDefault(MediaFormat.KEY_STRIDE, width)
                val sliceHeight = inputFormat.readPositiveOrDefault(
                    MediaFormat.KEY_SLICE_HEIGHT,
                    height
                )

                if (candidate.layout != null) {
                    Yuv420FrameConverter.requiredBufferSize(
                        width,
                        height,
                        stride,
                        sliceHeight
                    )
                }

                return StartedEncoder(
                    codec = codec,
                    candidate = candidate,
                    stride = stride,
                    sliceHeight = sliceHeight
                )
            } catch (error: Exception) {
                lastFailure = error
                if (started) {
                    try {
                        codec?.stop()
                    } catch (_: Exception) {
                    }
                }
                try {
                    codec?.release()
                } catch (_: Exception) {
                }
            }
        }

        throw IllegalStateException(
            "No se pudo iniciar ningún codificador H.264 compatible con este tamaño y FPS",
            lastFailure
        )
    }

    private fun findEncoderCandidates(): List<EncoderCandidate> {
        val candidates = mutableListOf<EncoderCandidate>()
        val formatOptions = listOf(
            FormatOption(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                layout = null,
                priority = 2
            ),
            FormatOption(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                layout = Yuv420Layout.SEMIPLANAR_UV,
                priority = 0
            ),
            FormatOption(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
                layout = Yuv420Layout.PLANAR_I420,
                priority = 1
            )
        )

        for (codecInfo in MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos) {
            if (!codecInfo.isEncoder) continue
            val avcType = codecInfo.supportedTypes.firstOrNull {
                it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
            } ?: continue

            val capabilities = try {
                codecInfo.getCapabilitiesForType(avcType)
            } catch (_: Exception) {
                continue
            }
            val videoCapabilities = capabilities.videoCapabilities ?: continue
            val supportsSizeAndRate = try {
                videoCapabilities.areSizeAndRateSupported(
                    width,
                    height,
                    fps.toDouble()
                )
            } catch (_: Exception) {
                false
            }
            if (!supportsSizeAndRate) continue

            val supportedFormats = capabilities.colorFormats.toSet()
            val hardwareRank = if (Build.VERSION.SDK_INT >= 29) {
                if (codecInfo.isHardwareAccelerated) 0 else 1
            } else {
                if (
                    codecInfo.name.startsWith("OMX.google.", ignoreCase = true) ||
                    codecInfo.name.startsWith("c2.android.", ignoreCase = true)
                ) 1 else 0
            }

            for (option in formatOptions) {
                if (option.colorFormat in supportedFormats) {
                    candidates += EncoderCandidate(
                        codecName = codecInfo.name,
                        colorFormat = option.colorFormat,
                        layout = option.layout,
                        hardwareRank = hardwareRank,
                        formatPriority = option.priority
                    )
                }
            }
        }

        return candidates.sortedWith(
            compareBy<EncoderCandidate> { it.hardwareRank }
                .thenBy { it.formatPriority }
                .thenBy { it.codecName }
        )
    }

    private fun queueFrame(
        encoder: MediaCodec,
        started: StartedEncoder,
        inputIndex: Int,
        argb: IntArray,
        presentationTimeUs: Long
    ) {
        if (started.candidate.layout == null) {
            // Capture capacity before getInputImage(): Android invalidates a
            // previously returned ByteBuffer when the Image view is requested.
            val inputCapacity = encoder.getInputBuffer(inputIndex)?.capacity()
                ?: throw IllegalStateException("El codificador no entregó un buffer de entrada")
            val image = encoder.getInputImage(inputIndex)
                ?: throw IllegalStateException("El codificador flexible no entregó una imagen YUV")
            try {
                val logicalSize = Yuv420FrameConverter.writeToImage(
                    argb,
                    width,
                    height,
                    image
                )
                require(inputCapacity >= logicalSize) {
                    "El buffer flexible del codificador es demasiado pequeño"
                }
            } finally {
                image.close()
            }
            encoder.queueInputBuffer(
                inputIndex,
                0,
                inputCapacity,
                presentationTimeUs,
                0
            )
        } else {
            val layout = started.candidate.layout
                ?: throw IllegalStateException("Formato YUV420 directo no seleccionado")
            val inputBuffer = encoder.getInputBuffer(inputIndex)
                ?: throw IllegalStateException("El codificador no entregó un buffer de entrada")
            inputBuffer.clear()
            val size = Yuv420FrameConverter.write(
                argb = argb,
                width = width,
                height = height,
                stride = started.stride,
                sliceHeight = started.sliceHeight,
                layout = layout,
                target = inputBuffer
            )
            encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
        }
    }

    private fun awaitInputBuffer(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        muxerState: MuxerState,
        bufferInfo: MediaCodec.BufferInfo
    ): Int {
        val deadline = SystemClock.elapsedRealtime() + INPUT_BUFFER_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val index = encoder.dequeueInputBuffer(POLL_TIMEOUT_US)
            if (index >= 0) return index

            // Free encoded output while waiting for an input slot. Without this,
            // a full output queue can prevent the encoder from returning input.
            drainAvailableOutput(encoder, muxer, muxerState, bufferInfo)
        }
        throw TimeoutException("Tiempo agotado esperando un buffer de entrada del codificador")
    }

    private fun drainAvailableOutput(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        muxerState: MuxerState,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        repeat(MAX_OUTPUT_DRAIN_PER_PASS) {
            when (drainOneOutput(encoder, muxer, muxerState, bufferInfo, 0L)) {
                DrainResult.NO_OUTPUT -> return
                DrainResult.END_OF_STREAM -> throw IllegalStateException(
                    "El codificador terminó antes de recibir EOS"
                )
                DrainResult.PROGRESS -> Unit
            }
        }
    }

    private fun drainUntilEndOfStream(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        muxerState: MuxerState,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        val deadline = SystemClock.elapsedRealtime() + EOS_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            when (
                drainOneOutput(
                    encoder,
                    muxer,
                    muxerState,
                    bufferInfo,
                    POLL_TIMEOUT_US
                )
            ) {
                DrainResult.END_OF_STREAM -> return
                DrainResult.NO_OUTPUT, DrainResult.PROGRESS -> Unit
            }
        }
        throw TimeoutException("Tiempo agotado esperando el final del codificador (EOS)")
    }

    private fun drainOneOutput(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        muxerState: MuxerState,
        bufferInfo: MediaCodec.BufferInfo,
        timeoutUs: Long
    ): DrainResult {
        val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
        when (outputIndex) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> return DrainResult.NO_OUTPUT
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                check(!muxerState.started) {
                    "El codificador cambió el formato de salida más de una vez"
                }
                muxerState.trackIndex = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                muxerState.started = true
                return DrainResult.PROGRESS
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> return DrainResult.PROGRESS
            else -> {
                if (outputIndex < 0) {
                    throw IllegalStateException("Respuesta desconocida del codificador")
                }
            }
        }

        var reachedEos = false
        try {
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                bufferInfo.size = 0
            }
            if (bufferInfo.size > 0) {
                check(muxerState.started && muxerState.trackIndex >= 0) {
                    "El codificador produjo datos antes de anunciar el formato de salida"
                }
                val outputBuffer: ByteBuffer = encoder.getOutputBuffer(outputIndex)
                    ?: throw IllegalStateException("El codificador devolvió un buffer de salida nulo")
                val end = bufferInfo.offset.toLong() + bufferInfo.size.toLong()
                require(
                    bufferInfo.offset >= 0 && bufferInfo.size >= 0 &&
                        end <= outputBuffer.capacity().toLong()
                ) { "El codificador devolvió un rango de salida inválido" }
                outputBuffer.position(bufferInfo.offset)
                outputBuffer.limit(end.toInt())
                muxer.writeSampleData(muxerState.trackIndex, outputBuffer, bufferInfo)
            }
            reachedEos = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
        } finally {
            encoder.releaseOutputBuffer(outputIndex, false)
        }

        return if (reachedEos) DrainResult.END_OF_STREAM else DrainResult.PROGRESS
    }

    private fun copyBitmapToArgb(bitmap: Bitmap, destination: IntArray) {
        require(!bitmap.isRecycled) { "El renderizador devolvió un Bitmap reciclado" }
        val source = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        try {
            source.getPixels(destination, 0, width, 0, 0, width, height)
        } finally {
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    private fun recommendedBitRate(): Int =
        (width.toLong() * height.toLong() * fps.toLong() / 10L)
            .coerceIn(1_000_000L, 12_000_000L)
            .toInt()

    private fun MediaFormat.readPositiveOrDefault(key: String, fallback: Int): Int {
        if (!containsKey(key)) return fallback
        val value = getInteger(key)
        return if (value > 0) value else fallback
    }

    private data class FormatOption(
        val colorFormat: Int,
        val layout: Yuv420Layout?,
        val priority: Int
    )

    private data class EncoderCandidate(
        val codecName: String,
        val colorFormat: Int,
        val layout: Yuv420Layout?,
        val hardwareRank: Int,
        val formatPriority: Int
    )

    private data class StartedEncoder(
        val codec: MediaCodec,
        val candidate: EncoderCandidate,
        val stride: Int,
        val sliceHeight: Int
    )

    private data class MuxerState(
        var trackIndex: Int = -1,
        var started: Boolean = false
    )

    private enum class DrainResult {
        NO_OUTPUT,
        PROGRESS,
        END_OF_STREAM
    }

    private companion object {
        const val POLL_TIMEOUT_US = 10_000L
        const val INPUT_BUFFER_TIMEOUT_MS = 15_000L
        const val EOS_TIMEOUT_MS = 30_000L
        const val MAX_OUTPUT_DRAIN_PER_PASS = 64

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
