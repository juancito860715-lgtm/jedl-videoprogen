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

    fun drawLayers(canvas: Canvas, canvasWidth: Int, canvasHeight: Int,
                   layers: List<LayerDocument>, assetResolver: (String) -> Bitmap?) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || layers.isEmpty()) return
        val baseSize = min(canvasWidth, canvasHeight) * 0.25f
        val imagePaint = requireNotNull(bitmapPaint.get())
        val captionPaint = requireNotNull(textPaint.get())
        layers.asSequence().filter { it.visible }.sortedBy { it.zIndex }.forEach { layer ->
            val save = canvas.save()
            canvas.translate(canvasWidth * layer.transform.x, canvasHeight * layer.transform.y)
            canvas.rotate(layer.transform.rotationDegrees)
            canvas.scale(layer.transform.scaleX, layer.transform.scaleY)
            when (layer.type) {
                LayerType.IMAGE, LayerType.CHARACTER, LayerType.BACKGROUND -> {
                    val bitmap = layer.assetPath?.let(assetResolver)
                    if (bitmap != null && !bitmap.isRecycled) {
                        val fit = min(baseSize / bitmap.width, baseSize / bitmap.height)
                        val width = bitmap.width * fit
                        val height = bitmap.height * fit
                        imagePaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(bitmap, null, RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), imagePaint)
                    }
                }
                LayerType.TEXT, LayerType.SUBTITLE -> if (layer.text.isNotEmpty()) {
                    captionPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                    captionPaint.textSize = (baseSize * 0.22f).coerceIn(12f, 160f)
                    canvas.drawText(layer.text, 0f, 0f, captionPaint)
                }
                LayerType.VIDEO, LayerType.AUDIO -> Unit
            }
            canvas.restoreToCount(save)
        }
    }
}
