package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

object SceneLayerRenderer {
    // Paint es mutable; cada hilo de render recibe su propia instancia.
    private val bitmapPaint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    private val textPaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(5f, 1f, 2f, android.graphics.Color.BLACK)
        }
    }

    /**
     * Dibuja solo las capas activas en el instante de escena solicitado.
     * Las imágenes normales caben en el 80% del lienzo; los fondos cubren
     * todo el lienzo (cover). La misma regla se usa en preview y export.
     */
    fun drawLayers(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        layers: List<LayerDocument>,
        assetResolver: (String) -> Bitmap?,
        sceneTimeMillis: Long = 0L
    ) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || layers.isEmpty() || sceneTimeMillis < 0L) return
        val imagePaint = requireNotNull(bitmapPaint.get())
        val captionPaint = requireNotNull(textPaint.get())
        layers.asSequence()
            .filter { ProjectLayerTiming.isActive(it, sceneTimeMillis) }
            .sortedBy { it.zIndex }
            .forEach { layer ->
                val save = canvas.save()
                canvas.translate(canvasWidth * layer.transform.x, canvasHeight * layer.transform.y)
                canvas.rotate(layer.transform.rotationDegrees)
                canvas.scale(layer.transform.scaleX, layer.transform.scaleY)
                when (layer.type) {
                    LayerType.IMAGE, LayerType.CHARACTER, LayerType.BACKGROUND -> {
                        val bitmap = layer.assetPath?.let(assetResolver)
                        if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                            val width: Float
                            val height: Float
                            if (layer.type == LayerType.BACKGROUND) {
                                val cover = maxOf(canvasWidth.toFloat() / bitmap.width, canvasHeight.toFloat() / bitmap.height)
                                width = bitmap.width * cover
                                height = bitmap.height * cover
                            } else {
                                val maxWidth = canvasWidth * 0.8f
                                val maxHeight = canvasHeight * 0.8f
                                val fit = min(maxWidth / bitmap.width, maxHeight / bitmap.height)
                                width = bitmap.width * fit
                                height = bitmap.height * fit
                            }
                            imagePaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                            val destination = RectF(-width / 2f, -height / 2f, width / 2f, height / 2f)
                            canvas.drawBitmap(bitmap, null, destination, imagePaint)
                        }
                    }
                    LayerType.TEXT, LayerType.SUBTITLE -> if (layer.text.isNotEmpty()) {
                        captionPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                        captionPaint.textSize = (min(canvasWidth, canvasHeight) * 0.055f).coerceIn(18f, 96f)
                        canvas.drawText(layer.text.take(500), 0f, 0f, captionPaint)
                    }
                    LayerType.VIDEO, LayerType.AUDIO -> Unit
                }
                canvas.restoreToCount(save)
            }
    }
}
