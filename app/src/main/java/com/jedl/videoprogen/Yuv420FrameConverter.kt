package com.jedl.videoprogen

import android.graphics.ImageFormat
import android.media.Image
import java.nio.ByteBuffer

internal enum class Yuv420Layout {
    SEMIPLANAR_UV,
    PLANAR_I420
}

/**
 * Converts ARGB pixels into the two byte-buffer YUV420 layouts explicitly
 * advertised by a MediaCodec encoder. Padding is taken from the codec input
 * format instead of assuming tightly packed width * height rows.
 */
internal object Yuv420FrameConverter {

    fun requiredBufferSize(
        width: Int,
        height: Int,
        stride: Int = width,
        sliceHeight: Int = height
    ): Int {
        require(width >= 2 && height >= 2 && width % 2 == 0 && height % 2 == 0) {
            "YUV420 requiere dimensiones pares de al menos 2 píxeles"
        }
        require(stride >= width && stride % 2 == 0) {
            "Stride YUV420 no válido"
        }
        require(sliceHeight >= height && sliceHeight % 2 == 0) {
            "Slice-height YUV420 no válido"
        }
        val ySize = stride.toLong() * sliceHeight.toLong()
        val total = ySize + ySize / 2L
        require(total <= Int.MAX_VALUE) { "Buffer YUV420 demasiado grande" }
        return total.toInt()
    }

    fun write(
        argb: IntArray,
        width: Int,
        height: Int,
        stride: Int,
        sliceHeight: Int,
        layout: Yuv420Layout,
        target: ByteBuffer
    ): Int {
        val required = requiredBufferSize(width, height, stride, sliceHeight)
        require(argb.size.toLong() >= width.toLong() * height.toLong()) {
            "El buffer ARGB no contiene todos los píxeles"
        }
        require(target.capacity() >= required) {
            "El buffer de entrada del codificador es insuficiente"
        }

        val ySize = stride * sliceHeight
        target.clear()
        for (index in 0 until required) {
            target.put(index, if (index < ySize) 16.toByte() else 128.toByte())
        }

        for (y in 0 until height) {
            val sourceRow = y * width
            val targetRow = y * stride
            for (x in 0 until width) {
                target.put(targetRow + x, luma(argb[sourceRow + x]))
            }
        }

        val chromaStride = stride / 2
        val chromaRows = sliceHeight / 2
        val uOffset = ySize
        val vOffset = uOffset + chromaStride * chromaRows

        for (cy in 0 until height / 2) {
            for (cx in 0 until width / 2) {
                val firstRow = (cy * 2) * width
                val secondRow = firstRow + width
                val c0 = argb[firstRow + cx * 2]
                val c1 = argb[firstRow + cx * 2 + 1]
                val c2 = argb[secondRow + cx * 2]
                val c3 = argb[secondRow + cx * 2 + 1]
                val r = (((c0 ushr 16) and 0xFF) + ((c1 ushr 16) and 0xFF) +
                    ((c2 ushr 16) and 0xFF) + ((c3 ushr 16) and 0xFF)) / 4
                val g = (((c0 ushr 8) and 0xFF) + ((c1 ushr 8) and 0xFF) +
                    ((c2 ushr 8) and 0xFF) + ((c3 ushr 8) and 0xFF)) / 4
                val b = ((c0 and 0xFF) + (c1 and 0xFF) +
                    (c2 and 0xFF) + (c3 and 0xFF)) / 4

                val chromaIndex = cy * stride + cx * 2
                val u = chromaU(r, g, b)
                val v = chromaV(r, g, b)
                when (layout) {
                    Yuv420Layout.SEMIPLANAR_UV -> {
                        target.put(uOffset + chromaIndex, u)
                        target.put(uOffset + chromaIndex + 1, v)
                    }
                    Yuv420Layout.PLANAR_I420 -> {
                        val planarIndex = cy * chromaStride + cx
                        target.put(uOffset + planarIndex, u)
                        target.put(vOffset + planarIndex, v)
                    }
                }
            }
        }

        target.position(0)
        target.limit(required)
        return required
    }

    /**
     * Writes through YUV_420_888 planes when the codec advertises the flexible
     * format. Plane row/pixel strides are honored; no NV12/NV21 assumption is
     * made for the Image-backed input buffer.
     */
    fun writeToImage(argb: IntArray, width: Int, height: Int, image: Image): Int {
        val logicalSize = requiredBufferSize(width, height)
        require(image.format == ImageFormat.YUV_420_888) {
            "El codificador no entregó una imagen YUV_420_888"
        }
        require(image.width == width && image.height == height) {
            "Las dimensiones de la imagen YUV no coinciden con el fotograma"
        }
        val crop = image.cropRect
        require(crop.left == 0 && crop.top == 0 && crop.right == width && crop.bottom == height) {
            "El codificador devolvió un recorte YUV no compatible"
        }
        require(argb.size.toLong() >= width.toLong() * height.toLong()) {
            "El buffer ARGB no contiene todos los píxeles"
        }
        val planes = image.planes
        require(planes.size == 3) { "La imagen YUV no contiene tres planos" }

        val yPlane = PlaneWriter(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride)
        val uPlane = PlaneWriter(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride)
        val vPlane = PlaneWriter(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride)

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                yPlane.put(x, y, luma(argb[row + x]))
            }
        }

        for (cy in 0 until height / 2) {
            for (cx in 0 until width / 2) {
                val firstRow = (cy * 2) * width
                val secondRow = firstRow + width
                val c0 = argb[firstRow + cx * 2]
                val c1 = argb[firstRow + cx * 2 + 1]
                val c2 = argb[secondRow + cx * 2]
                val c3 = argb[secondRow + cx * 2 + 1]
                val r = (((c0 ushr 16) and 0xFF) + ((c1 ushr 16) and 0xFF) +
                    ((c2 ushr 16) and 0xFF) + ((c3 ushr 16) and 0xFF)) / 4
                val g = (((c0 ushr 8) and 0xFF) + ((c1 ushr 8) and 0xFF) +
                    ((c2 ushr 8) and 0xFF) + ((c3 ushr 8) and 0xFF)) / 4
                val b = ((c0 and 0xFF) + (c1 and 0xFF) +
                    (c2 and 0xFF) + (c3 and 0xFF)) / 4
                uPlane.put(cx, cy, chromaU(r, g, b))
                vPlane.put(cx, cy, chromaV(r, g, b))
            }
        }
        return logicalSize
    }

    private class PlaneWriter(
        private val buffer: ByteBuffer,
        private val rowStride: Int,
        private val pixelStride: Int
    ) {
        private val basePosition = buffer.position()
        private val limit = buffer.limit()

        init {
            require(rowStride > 0 && pixelStride > 0) { "Stride de plano YUV no válido" }
        }

        fun put(x: Int, y: Int, value: Byte) {
            val index = basePosition.toLong() + y.toLong() * rowStride +
                x.toLong() * pixelStride
            require(index >= basePosition && index < limit) {
                "El plano YUV no tiene capacidad suficiente para sus strides"
            }
            buffer.put(index.toInt(), value)
        }
    }

    private fun luma(color: Int): Byte {
        val r = (color ushr 16) and 0xFF
        val g = (color ushr 8) and 0xFF
        val b = color and 0xFF
        return (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16)
            .coerceIn(16, 235).toByte()
    }

    private fun chromaU(r: Int, g: Int, b: Int): Byte =
        (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128)
            .coerceIn(16, 240).toByte()

    private fun chromaV(r: Int, g: Int, b: Int): Byte =
        (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128)
            .coerceIn(16, 240).toByte()
}
