package com.jedl.videoprogen

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.min
import kotlin.math.sin

class MainActivity : Activity() {
    private lateinit var previewSurface: SurfaceView
    private lateinit var statusText: TextView
    private lateinit var poseDetector: PoseDetector
    private var photo: Bitmap? = null
    private var joints: List<PointF> = emptyList()
    @Volatile private var animProgress: Float = 0f
    private var frameClock: FrameClock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = this as Context 

        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0C10"))
            setPadding(20, 20, 20, 20)
        }
        previewSurface = SurfaceView(ctx)
        statusText = TextView(ctx).apply { 
            text = "Listo · elegí una foto"; setTextColor(Color.WHITE); textSize = 14f 
        }
        val btnPick = Button(ctx).apply { text = "1. Elegir foto" }
        val btnAnimate = Button(ctx).apply { text = "2. Animar A→B (3s)" }
        val btnExport = Button(ctx).apply { text = "3. Exportar MP4 a Galería" }

        layout.addView(previewSurface, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(statusText, LinearLayout.LayoutParams(-1, -2))
        layout.addView(btnPick, LinearLayout.LayoutParams(-1, -2))
        layout.addView(btnAnimate, LinearLayout.LayoutParams(-1, -2))
        layout.addView(btnExport, LinearLayout.LayoutParams(-1, -2))
        setContentView(layout)

        try { poseDetector = PoseDetector(ctx) } catch (e: Exception) { statusText.text = "Error MediaPipe: ${e.message}" }

        btnPick.setOnClickListener { 
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, 100) 
        }
        btnAnimate.setOnClickListener { startAnimation() }
        btnExport.setOnClickListener { exportVideo() }
        
        previewSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) { drawPreview() }
            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { drawPreview() }
            override fun surfaceDestroyed(h: SurfaceHolder) {}
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.data != null) {
            loadPhoto(data.data!!)
        }
    }

    private fun loadPhoto(uri: Uri) {
        statusText.text = "Cargando foto y detectando juntas…"
        Thread {
            val bitmap = try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            if (bitmap == null) { runOnUiThread { statusText.text = "No se pudo cargar" }; return@Thread }
            val detected = try { poseDetector.detectJoints(bitmap) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                photo = bitmap; joints = detected; animProgress = 0f
                statusText.text = if (detected.isEmpty()) "Foto cargada · 0 juntas" else "Foto cargada · ${detected.size} juntas"
                drawPreview()
            }
        }.start()
    }

    private fun startAnimation() {
        if (photo == null) { statusText.text = "Elegí una foto primero"; return }
        frameClock?.stop(); animProgress = 0f; statusText.text = "Animando (respiración y esqueleto)…"
        val clock = FrameClock(fps = 30) { frame ->
            val p = min(1f, frame / (30f * 3f))
            animProgress = p; runOnUiThread { drawPreview() }
            if (p >= 1f) runOnUiThread { statusText.text = "Listo para exportar" }
        }
        frameClock = clock; clock.start()
    }

    private fun exportVideo() {
        if (photo == null) { statusText.text = "Elegí una foto primero"; return }
        statusText.text = "Exportando MP4 a la Galería…"
        val bmp = photo!!; val jts = joints
        
        Thread {
            try {
                // 1. Crear archivo temporal
                val tempFile = File(cacheDir, "temp_export.mp4")
                val exporter = Mp4Exporter(720, 1280, 30, 5f)
                val ok = exporter.export(tempFile.absolutePath, { pct -> runOnUiThread { statusText.text = "Exportando… $pct%" } }) { progress -> renderSceneToBitmap(bmp, jts, progress, 720, 1280) }

                if (ok) {
                    // 2. Copiar a la Galería (MediaStore)
                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "JEDL_Video_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/JEDL")
                        }
                    }
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(out)
                            }
                        }
                        runOnUiThread { statusText.text = "✓ ¡Guardado en tu Galería (películas/JEDL)!" }
                    } else {
                        runOnUiThread { statusText.text = "✓ Guardado en: ${tempFile.absolutePath}" }
                    }
                } else {
                    runOnUiThread { statusText.text = "Falló la exportación" }
                }
            } catch (e: Exception) { 
                runOnUiThread { statusText.text = "Error: ${e.message}" } 
            }
        }.start()
    }

    private fun drawPreview() {
        val holder = previewSurface.holder; val canvas = holder.lockCanvas() ?: return
        try { renderScene(canvas, canvas.width, canvas.height, animProgress, photo, joints) } finally { holder.unlockCanvasAndPost(canvas) }
    }

    private fun renderScene(canvas: Canvas, w: Int, h: Int, progress: Float, bmp: Bitmap?, jts: List<PointF>) {
        canvas.drawColor(Color.parseColor("#0B0C10"))
        bmp?.let {
            // EFECTO DE ANIMACIÓN: Hacemos que la imagen "respire" (sube y baja de tamaño)
            val bounce = 1.0f + (sin(progress * Math.PI * 6).toFloat() * 0.05f)
            val baseScale = min(w.toFloat() / it.width, h.toFloat() / it.height) * 0.85f
            val scale = baseScale * bounce

            val dw = it.width * scale; val dh = it.height * scale
            val dx = (w - dw) / 2f; val dy = (h - dh) / 2f
            canvas.drawBitmap(it, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))

            // Dibujar los puntos escalados
            val scaledJoints = jts.map { PointF(dx + (it.x / bmp.width) * dw, dy + (it.y / bmp.height) * dh) }
            
            // ESQUELETO: Dibujamos líneas entre los puntos clave de MediaPipe
            val skeletonPaint = Paint().apply { color = Color.parseColor("#E87722"); strokeWidth = 5f; style = Paint.Style.STROKE }
            val jointPaint = Paint().apply { color = Color.RED }
            
            // Conexiones básicas: Hombros, brazos, torso, piernas
            val connections = listOf(
                Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16),
                Pair(11, 23), Pair(12, 24), Pair(23, 24), 
                Pair(23, 25), Pair(25, 27), Pair(24, 26), Pair(26, 28)
            )
            for ((i, j) in connections) {
                if (i < scaledJoints.size && j < scaledJoints.size) {
                    canvas.drawLine(scaledJoints[i].x, scaledJoints[i].y, scaledJoints[j].x, scaledJoints[j].y, skeletonPaint)
                }
            }

            // Dibujar puntos encima de las líneas
            for (j in scaledJoints) {
                canvas.drawCircle(j.x, j.y, 6f, jointPaint)
            }
        }

        // Barra de progreso inferior
        canvas.drawRect(0f, h - 10f, w.toFloat(), h.toFloat(), Paint().apply { color = Color.parseColor("#262932") })
        canvas.drawRect(0f, h - 10f, w * progress, h.toFloat(), Paint().apply { color = Color.parseColor("#E87722") })
    }

    private fun renderSceneToBitmap(bmp: Bitmap, jts: List<PointF>, progress: Float, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, bmp, jts)
        return out
    }
}
