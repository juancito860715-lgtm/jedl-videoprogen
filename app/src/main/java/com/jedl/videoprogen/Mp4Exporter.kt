package com.jedl.videoprogen

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Exporta el motor de escenas a MP4/H.264 usando el codificador disponible en Android.
 *
 * La entrada YUV se prepara en el formato que el codificador declara soportar:
 * NV12 (YUV420 semi-planar) o I420 (YUV420 planar). No se presupone que todos
 * los teléfonos acepten el mismo formato.
 */
class Mp4Exporter(
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    private val durationSeconds: Float
) {
    companion object {
        private const val TAG = "Mp4Exporter"
        private const val MIME_TYPE = "video/avc"
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val INPUT_BUFFER_TIMEOUT_MS = 15_000L
        private const val EOS_TIMEOUT_MS = 120_000L
        private const val MAX_DIMENSION = 1_920
        private const val MAX_PIXELS = 2_073_600L // 1080 x 1920
    }

    private enum class YuvLayout {
        NV12,
        I420
    }

    private data class EncoderSelection(
        val codec: MediaCodec,
        val layout: YuvLayout
    )

    private class MuxerState(val muxer: MediaMuxer) {
        var trackIndex: Int = -1
        var started: Boolean = false
    }

    /**
     * Devuelve true solo si el MP4 se cerró correctamente y se publicó en outPath.
     * Ante un error, el archivo temporal se elimina y el archivo de destino anterior
     * (si existía) se conserva.
     */
    fun export(
        outPath: String,
        onProgress: (Int) -> Unit,
        renderFrame: (Float) -> Bitmap
    ): Boolean {
        val totalFrames = validateAndGetFrameCount() ?: return false
        if (outPath.isBlank()) {
            Log.e(TAG, "La ruta de salida está vacía")
            return false
        }

        val target = File(outPath).absoluteFile
        val parent = target.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            Log.e(TAG, "No se pudo crear el directorio de salida: $parent")
            return false
        }

        var stagingFile: File? = null
        var encoder: MediaCodec? = null
        var encoderStarted = false
        var muxer: MediaMuxer? = null
        var muxerState: MuxerState? = null
        var outputReady = false

        try {
            // Se escribe primero en un archivo temporal. El destino definitivo no se
            // toca hasta que MediaMuxer haya cerrado correctamente el contenedor.
            val currentStagingFile = File.createTempFile("jedl-export-", ".mp4", parent)
            stagingFile = currentStagingFile

            val selection = createStartedEncoder()
            val activeEncoder = selection.codec
            encoder = activeEncoder
            encoderStarted = true

            val inputFormat = activeEncoder.inputFormat
            val stride = readFormatInteger(inputFormat, MediaFormat.KEY_STRIDE, width)
            val sliceHeight = readFormatInteger(inputFormat, MediaFormat.KEY_SLICE_HEIGHT, height)
            validateInputLayout(stride, sliceHeight)

            val yPlaneSize = stride * sliceHeight
            val yuvSize = yPlaneSize + yPlaneSize / 2
            val pixelBuffer = IntArray(width * height)
            val yuvBuffer = ByteArray(yuvSize)

            val createdMuxer = MediaMuxer(
                currentStagingFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            muxer = createdMuxer
            val state = MuxerState(createdMuxer)
            muxerState = state
            val bufferInfo = MediaCodec.BufferInfo()

            for (frameIndex in 0 until totalFrames) {
                val bitmap = renderFrame(
                    if (totalFrames == 1) 1f
                    else frameIndex.toFloat() / (totalFrames - 1).toFloat()
                )

                try {
                    bitmapToYuv(
                        bitmap = bitmap,
                        pixels = pixelBuffer,
                        output = yuvBuffer,
                        stride = stride,
                        sliceHeight = sliceHeight,
                        layout = selection.layout
                    )
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

                val inputIndex = awaitInputBuffer(activeEncoder, state, bufferInfo)
                val inputBuffer = activeEncoder.getInputBuffer(inputIndex)
                    ?: throw IOException("El codificador devolvió un buffer de entrada nulo")

                inputBuffer.clear()
                if (inputBuffer.remaining() < yuvBuffer.size) {
                    throw IOException(
                        "Buffer de entrada insuficiente: ${inputBuffer.remaining()} < ${yuvBuffer.size}"
                    )
                }
                inputBuffer.put(yuvBuffer)

                val presentationTimeUs = frameIndex.toLong() * 1_000_000L / fps
                activeEncoder.queueInputBuffer(
                    inputIndex,
                    0,
                    yuvBuffer.size,
                    presentationTimeUs,
                    0
                )

                // Drenar después de cada frame evita bloquear el codificador cuando
                // sus buffers de salida se llenan.
                if (drainOutput(activeEncoder, state, bufferInfo, waitForEos = false)) {
                    throw IOException("El codificador terminó antes de recibir todos los frames")
                }

                safelyReportProgress(onProgress, ((frameIndex + 1) * 100) / totalFrames)
            }

            // Señal de fin de entrada. Mientras esperamos el EOS seguimos drenando
            // la salida; la espera tiene un límite para evitar bucles infinitos.
            val eosInputIndex = awaitInputBuffer(activeEncoder, state, bufferInfo)
            val endPresentationTimeUs = totalFrames.toLong() * 1_000_000L / fps
            activeEncoder.queueInputBuffer(
                eosInputIndex,
                0,
                0,
                endPresentationTimeUs,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )

            val eosDeadline = System.nanoTime() + EOS_TIMEOUT_MS * 1_000_000L
            val eosReceived = drainOutput(
                activeEncoder,
                state,
                bufferInfo,
                waitForEos = true,
                deadlineNanos = eosDeadline
            )
            if (!eosReceived) {
                throw IOException("El codificador no confirmó el fin del video a tiempo")
            }
            if (!state.started || state.trackIndex < 0) {
                throw IOException("El codificador no entregó un formato de salida válido")
            }

            // Si stop() falla, outputReady sigue siendo false y el temporal se elimina.
            createdMuxer.stop()
            state.started = false
            outputReady = true
        } catch (error: Exception) {
            Log.e(TAG, "Falló la exportación MP4", error)
        } finally {
            val activeEncoder = encoder
            if (activeEncoder != null) {
                if (encoderStarted) {
                    try {
                        activeEncoder.stop()
                    } catch (error: Exception) {
                        Log.w(TAG, "No se pudo detener el codificador limpiamente", error)
                    }
                }
                try {
                    activeEncoder.release()
                } catch (error: Exception) {
                    Log.w(TAG, "No se pudo liberar el codificador limpiamente", error)
                }
            }

            val activeMuxer = muxer
            if (activeMuxer != null) {
                val state = muxerState
                if (state?.started == true) {
                    try {
                        activeMuxer.stop()
                    } catch (error: Exception) {
                        Log.w(TAG, "No se pudo detener el muxer durante la limpieza", error)
                    }
                }
                try {
                    activeMuxer.release()
                } catch (error: Exception) {
                    Log.w(TAG, "No se pudo liberar el muxer limpiamente", error)
                }
            }

            if (!outputReady) {
                stagingFile?.delete()
            }
        }

        val completedFile = stagingFile
        if (!outputReady || completedFile == null) return false

        return try {
            publishFile(completedFile, target)
            safelyReportProgress(onProgress, 100)
            true
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo publicar el MP4 terminado", error)
            completedFile.delete()
            false
        }
    }

    private fun validateAndGetFrameCount(): Int? {
        if (width <= 0 || height <= 0 ||
            width > MAX_DIMENSION || height > MAX_DIMENSION ||
            width % 2 != 0 || height % 2 != 0 ||
            width.toLong() * height.toLong() > MAX_PIXELS
        ) {
            Log.e(TAG, "Dimensiones no válidas para YUV420: ${width}x${height}")
            return null
        }

        if (fps !in 1..60) {
            Log.e(TAG, "FPS fuera del rango admitido: $fps")
            return null
        }

        if (!durationSeconds.isFinite() || durationSeconds <= 0f) {
            Log.e(TAG, "Duración no válida: $durationSeconds")
            return null
        }

        val frameCount = fps.toDouble() * durationSeconds.toDouble()
        if (!frameCount.isFinite() || frameCount < 1.0 || frameCount > Int.MAX_VALUE.toDouble()) {
            Log.e(TAG, "Cantidad de frames no válida: $frameCount")
            return null
        }

        return frameCount.toInt()
    }

    private fun createStartedEncoder(): EncoderSelection {
        val codecInfos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        val preferredFormats = listOf(
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar to YuvLayout.NV12,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar to YuvLayout.I420
        )

        for ((colorFormat, layout) in preferredFormats) {
            for (codecInfo in codecInfos) {
                if (!codecInfo.isEncoder ||
                    codecInfo.supportedTypes.none { it.equals(MIME_TYPE, ignoreCase = true) }
                ) {
                    continue
                }

                val capabilities = try {
                    codecInfo.getCapabilitiesForType(MIME_TYPE)
                } catch (_: Exception) {
                    continue
                }
                if (!capabilities.colorFormats.contains(colorFormat)) continue

                var candidate: MediaCodec? = null
                var started = false
                try {
                    candidate = MediaCodec.createByCodecName(codecInfo.name)
                    val desiredBitrate = (
                        width.toLong() * height.toLong() * fps.toLong() / 10L
                    ).coerceIn(1_500_000L, 12_000_000L).toInt()
                    val bitrateRange = capabilities.videoCapabilities?.bitrateRange
                    val bitrate = if (bitrateRange != null) {
                        desiredBitrate.coerceIn(bitrateRange.lower, bitrateRange.upper)
                    } else {
                        desiredBitrate
                    }

                    val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                        setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                    }

                    candidate.configure(
                        format,
                        null,
                        null,
                        MediaCodec.CONFIGURE_FLAG_ENCODE
                    )
                    candidate.start()
                    started = true
                    Log.i(
                        TAG,
                        "Codificador seleccionado: ${codecInfo.name}, " +
                            if (layout == YuvLayout.NV12) "NV12" else "I420"
                    )
                    return EncoderSelection(candidate, layout)
                } catch (error: Exception) {
                    Log.w(TAG, "No se pudo iniciar ${codecInfo.name} con $layout", error)
                    if (started) {
                        try {
                            candidate?.stop()
                        } catch (_: Exception) {
                        }
                    }
                    try {
                        candidate?.release()
                    } catch (_: Exception) {
                    }
                }
            }
        }

        throw IOException(
            "No se encontró un codificador H.264 compatible con entrada YUV420 " +
                "semi-planar o planar para ${width}x${height} a ${fps} FPS"
        )
    }

    private fun awaitInputBuffer(
        encoder: MediaCodec,
        state: MuxerState,
        bufferInfo: MediaCodec.BufferInfo
    ): Int {
        val deadline = System.nanoTime() + INPUT_BUFFER_TIMEOUT_MS * 1_000_000L

        while (System.nanoTime() < deadline) {
            val inputIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (inputIndex >= 0) return inputIndex

            if (drainOutput(encoder, state, bufferInfo, waitForEos = false)) {
                throw IOException("El codificador terminó antes de tiempo")
            }
        }

        throw IOException("Tiempo agotado esperando un buffer de entrada del codificador")
    }

    /**
     * Drena los buffers disponibles. Si waitForEos=true, espera hasta EOS o hasta
     * alcanzar deadlineNanos. Devuelve true únicamente al recibir BUFFER_FLAG_END_OF_STREAM.
     */
    private fun drainOutput(
        encoder: MediaCodec,
        state: MuxerState,
        bufferInfo: MediaCodec.BufferInfo,
        waitForEos: Boolean,
        deadlineNanos: Long = 0L
    ): Boolean {
        while (true) {
            val timeoutUs = if (waitForEos) DEQUEUE_TIMEOUT_US else 0L
            val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEos) return false
                    if (System.nanoTime() >= deadlineNanos) {
                        throw IOException("Tiempo agotado esperando el fin del codificador")
                    }
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (state.started) {
                        throw IOException("El formato de salida del codificador cambió más de una vez")
                    }
                    state.trackIndex = state.muxer.addTrack(encoder.outputFormat)
                    state.muxer.start()
                    state.started = true
                }

                outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                    // getOutputBuffer() se consulta en cada iteración; no hay arrays que renovar.
                }

                outputIndex >= 0 -> {
                    var eosReceived = false
                    try {
                        val flags = bufferInfo.flags
                        val isCodecConfig = flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0

                        if (bufferInfo.size > 0 && !isCodecConfig) {
                            if (!state.started || state.trackIndex < 0) {
                                throw IOException("Se recibió un frame antes de iniciar el muxer")
                            }

                            val outputBuffer = encoder.getOutputBuffer(outputIndex)
                                ?: throw IOException("El codificador devolvió un buffer de salida nulo")
                            val end = bufferInfo.offset.toLong() + bufferInfo.size.toLong()
                            if (bufferInfo.offset < 0 || bufferInfo.size < 0 ||
                                end > outputBuffer.capacity().toLong()
                            ) {
                                throw IOException("El codificador devolvió límites de buffer inválidos")
                            }

                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(end.toInt())
                            state.muxer.writeSampleData(
                                state.trackIndex,
                                outputBuffer,
                                bufferInfo
                            )
                        }

                        eosReceived = flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }

                    if (eosReceived) return true
                }

                else -> {
                    if (!waitForEos) return false
                }
            }
        }
    }

    private fun readFormatInteger(format: MediaFormat, key: String, fallback: Int): Int {
        return if (format.containsKey(key)) format.getInteger(key) else fallback
    }

    private fun validateInputLayout(stride: Int, sliceHeight: Int) {
        if (stride < width || sliceHeight < height ||
            stride % 2 != 0 || sliceHeight % 2 != 0
        ) {
            throw IOException(
                "Alineación YUV no compatible: stride=$stride, sliceHeight=$sliceHeight"
            )
        }

        val requiredBytes = stride.toLong() * sliceHeight.toLong() * 3L / 2L
        if (requiredBytes <= 0L || requiredBytes > Int.MAX_VALUE.toLong()) {
            throw IOException("Tamaño de buffer YUV no válido: $requiredBytes")
        }
    }

    /**
     * Convierte ARGB a NV12 o I420 respetando stride/slice-height del codificador.
     * Los arrays se reutilizan durante toda la exportación para reducir presión de GC.
     */
    private fun bitmapToYuv(
        bitmap: Bitmap,
        pixels: IntArray,
        output: ByteArray,
        stride: Int,
        sliceHeight: Int,
        layout: YuvLayout
    ) {
        if (bitmap.isRecycled) {
            throw IOException("El motor devolvió un bitmap que ya estaba liberado")
        }

        val source = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        try {
            source.getPixels(pixels, 0, width, 0, 0, width, height)

            val yPlaneSize = stride * sliceHeight
            java.util.Arrays.fill(output, 0, yPlaneSize, 16.toByte())
            java.util.Arrays.fill(output, yPlaneSize, output.size, 128.toByte())

            // Plano Y.
            for (y in 0 until height) {
                val sourceRow = y * width
                val targetRow = y * stride
                for (x in 0 until width) {
                    val color = pixels[sourceRow + x]
                    val r = (color shr 16) and 0xff
                    val g = (color shr 8) and 0xff
                    val b = color and 0xff
                    val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                    output[targetRow + x] = yValue.coerceIn(16, 235).toByte()
                }
            }

            // Planos U/V, con submuestreo 4:2:0 (promedio de cada bloque 2x2).
            val chromaPlaneSize = yPlaneSize / 4
            for (y in 0 until height step 2) {
                for (x in 0 until width step 2) {
                    var red = 0
                    var green = 0
                    var blue = 0

                    for (dy in 0..1) {
                        val row = (y + dy) * width
                        for (dx in 0..1) {
                            val color = pixels[row + x + dx]
                            red += (color shr 16) and 0xff
                            green += (color shr 8) and 0xff
                            blue += color and 0xff
                        }
                    }

                    val r = (red + 2) / 4
                    val g = (green + 2) / 4
                    val b = (blue + 2) / 4
                    val u = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
                        .coerceIn(16, 240).toByte()
                    val v = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
                        .coerceIn(16, 240).toByte()

                    if (layout == YuvLayout.NV12) {
                        val uvIndex = yPlaneSize + (y / 2) * stride + x
                        output[uvIndex] = u
                        output[uvIndex + 1] = v
                    } else {
                        val chromaIndex = (y / 2) * (stride / 2) + (x / 2)
                        output[yPlaneSize + chromaIndex] = u
                        output[yPlaneSize + chromaPlaneSize + chromaIndex] = v
                    }
                }
            }
        } finally {
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    private fun safelyReportProgress(callback: (Int) -> Unit, percent: Int) {
        try {
            callback(percent.coerceIn(0, 100))
        } catch (error: Exception) {
            Log.w(TAG, "El callback de progreso produjo un error", error)
        }
    }

    private fun publishFile(stagingFile: File, target: File) {
        try {
            Files.move(
                stagingFile.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                stagingFile.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }
}
