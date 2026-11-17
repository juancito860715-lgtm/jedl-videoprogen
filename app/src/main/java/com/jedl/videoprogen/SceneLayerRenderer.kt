package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.min

object SceneLayerRenderer {
    private val bitmapPaint = ThreadLocal.withInitial { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    private val textPaint = ThreadLocal.withInitial {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            setShadowLayer(5f, 1f, 2f, android.graphics.Color.BLACK)
        }
    }

    fun drawLayers(
        canvas: Canvas,
        canvasWidth: Int,
        canvasHeight: Int,
        layers: List<LayerDocument>,
        assetResolver: (String) -> Bitmap?,
        sceneTimeMillis: Long = 0L,
        characterRenderer: ((LayerDocument) -> Unit)? = null
    ): SceneLayerRenderStats {
        if (canvasWidth <= 0 || canvasHeight <= 0 || sceneTimeMillis < 0L) {
            return SceneLayerRenderStats(0, 0)
        }
        val imagePaint = bitmapPaint.get()
        val captionPaint = textPaint.get()
        var drawn = 0
        var failed = 0
        for (layer in layers.sortedBy { it.zIndex }) {
            if (!layer.visible || !ProjectLayerTiming.isActive(layer, sceneTimeMillis)) continue
            val save = canvas.save()
            try {
                if (layer.type == LayerType.CHARACTER && characterRenderer != null) {
                    canvas.saveLayerAlpha(null, (layer.opacity * 255f).toInt().coerceIn(0, 255))
                }
                val isCharacter = layer.type == LayerType.CHARACTER
                val layerX = if (isCharacter) 0.5f else layer.transform.x
                val layerY = if (isCharacter) 0.5f else layer.transform.y
                val scaleX = if (isCharacter) layer.transform.scaleX.coerceIn(0.25f, 1.25f) else layer.transform.scaleX
                val scaleY = if (isCharacter) layer.transform.scaleY.coerceIn(0.25f, 1.25f) else layer.transform.scaleY
                canvas.translate(canvasWidth * layerX, canvasHeight * layerY)
                canvas.rotate(layer.transform.rotationDegrees)
                canvas.scale(scaleX, scaleY)
                when (layer.type) {
                    LayerType.IMAGE, LayerType.BACKGROUND -> {
                        val bitmap = layer.assetPath?.let(assetResolver)
                        if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                            val width: Float
                            val height: Float
                            if (layer.type == LayerType.BACKGROUND) {
                                val cover = maxOf(canvasWidth.toFloat() / bitmap.width, canvasHeight.toFloat() / bitmap.height)
                                width = bitmap.width * cover
                                height = bitmap.height * cover
                            } else {
                                val fit = min((canvasWidth * 0.8f) / bitmap.width, (canvasHeight * 0.8f) / bitmap.height)
                                width = bitmap.width * fit
                                height = bitmap.height * fit
                            }
                            imagePaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                            canvas.drawBitmap(bitmap, null, RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), imagePaint)
                            drawn++
                        } else {
                            failed++
                            Log.w("JEDL.SceneRenderer", "Layer ${layer.id} ${layer.type}: asset missing or invalid")
                        }
                    }
                    LayerType.CHARACTER -> {
                        if (characterRenderer != null) {
                            characterRenderer.invoke(layer)
                            drawn++
                        } else {
                            val bitmap = layer.assetPath?.let(assetResolver)
                            if (bitmap != null && !bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) {
                                val fit = min((canvasWidth * 0.8f) / bitmap.width, (canvasHeight * 0.8f) / bitmap.height)
                                val width = bitmap.width * fit
                                val height = bitmap.height * fit
                                imagePaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                                canvas.drawBitmap(bitmap, null, RectF(-width / 2f, -height / 2f, width / 2f, height / 2f), imagePaint)
                                drawn++
                            } else {
                                failed++
                                Log.w("JEDL.SceneRenderer", "Character layer ${layer.id}: no renderer or bitmap")
                            }
                        }
                    }
                    LayerType.TEXT, LayerType.SUBTITLE -> {
                        if (layer.text.isNotEmpty()) {
                            captionPaint.alpha = (layer.opacity * 255f).toInt().coerceIn(0, 255)
                            captionPaint.textSize = (min(canvasWidth, canvasHeight) * 0.055f).coerceIn(18f, 96f)
                            canvas.drawText(layer.text.take(500), 0f, 0f, captionPaint)
                            drawn++
                        }
                    }
                    LayerType.VIDEO, LayerType.AUDIO -> Unit
                }
            } catch (error: Exception) {
                failed++
                Log.w("JEDL.SceneRenderer", "Layer ${layer.id} failed; continuing export", error)
            } finally {
                canvas.restoreToCount(save)
            }
        }
        return SceneLayerRenderStats(drawn, failed)
    }
}
