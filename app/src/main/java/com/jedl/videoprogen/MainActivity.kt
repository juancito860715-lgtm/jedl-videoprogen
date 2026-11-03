package com.jedl.videoprogen

import android.app.Activity
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
import android.os.Bundle
import android.os.Environment
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlin.math.min

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
        val btnAnimate = Button(ctx).apply { text = "2. Animar A→B (2s)" }
        val btnExport = Button(ctx).apply { text = "3. Exportar MP4 de 5s" }

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
        frameClock?.stop(); animProgress = 0f; statusText.text = "Animando A→B…"
        val clock = FrameClock(fps = 30) { frame ->
            val p = min(1f, frame / (30f * 2f))
            animProgress = p; runOnUiThread { drawPreview() }
            if (p >= 1f) runOnUiThread { statusText.text = "Listo para exportar" }
        }
        frameClock = clock; clock.start()
    }

    private fun exportVideo() {
        if (photo == null) { statusText.text = "Elegí una foto primero"; return }
        val outDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: filesDir
        val outFile = File(outDir, "jedl_export_${System.currentTimeMillis()}.mp4")
        statusText.text = "Exportando MP4…"
        val bmp = photo!!; val jts = joints
        
        Thread {
            try {
                val exporter = Mp4Exporter(720, 1280, 30, 5f)
                val ok = exporter.export(outFile.absolutePath, { pct -> runOnUiThread { statusText.text = "Exportando… $pct%" } }) { progress -> renderSceneToBitmap(bmp, jts, progress, 720, 1280) }
                runOnUiThread { statusText.text = if (ok) "✓ Guardado:\n${outFile.absolutePath}" else "Falló" }
            } catch (e: Exception) { runOnUiThread { statusText.text = "Error: ${e.message}" } }
        }.start()
    }

    private fun drawPreview() {
        val holder = previewSurface.holder; val canvas = holder.lockCanvas() ?: return
        try { renderScene(canvas, canvas.width, canvas.height, animProgress, photo, joints) } finally { holder.unlockCanvasAndPost(canvas) }
    }

    private fun renderScene(canvas: Canvas, w: Int, h: Int, progress: Float, bmp: Bitmap?, jts: List<PointF>) {
        canvas.drawColor(Color.parseColor("#0B0C10"))
        bmp?.let {
            val scale = min(w.toFloat() / it.width, h.toFloat() / it.height) * 0.85f
            val dw = it.width * scale; val dh = it.height * scale
            val dx = (w - dw) / 2f; val dy = (h - dh) / 2f
            canvas.drawBitmap(it, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            val jointPaint = Paint().apply { color = Color.parseColor("#E87722") }
            for (j in jts) canvas.drawCircle(dx + j.x * scale, dy + j.y * scale, 5f, jointPaint)
        }
        val startX = w * 0.2f; val endX = w * 0.8f; val y = h * 0.88f
        val x = startX + (endX - startX) * progress
        canvas.drawCircle(x, y, 30f, Paint().apply { color = Color.parseColor("#E87722") })
        canvas.drawRect(0f, h - 6f, w.toFloat(), h.toFloat(), Paint().apply { color = Color.parseColor("#262932") })
        canvas.drawRect(0f, h - 6f, w * progress, h.toFloat(), Paint().apply { color = Color.parseColor("#E87722") })
    }

    private fun renderSceneToBitmap(bmp: Bitmap, jts: List<PointF>, progress: Float, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, bmp, jts)
        return out
    }
}
