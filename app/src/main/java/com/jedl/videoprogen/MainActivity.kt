package com.jedl.videoprogen

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
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import kotlin.math.min
import kotlin.math.sin

val Ink = ComposeColor(0xFF0B0C10)
val Panel = ComposeColor(0xFF14151A)
val Gold = ComposeColor(0xFFF5B841)
val TextGray = ComposeColor(0xFFE7E8EE)

class MainActivity : ComponentActivity() {
    private var previewSurface: SurfaceView? = null
    private lateinit var poseDetector: PoseDetector
    private var photo: Bitmap? = null
    private var joints: List<PointF> = emptyList()
    
    @Volatile private var animProgress: Float = 0f
    private var frameClock: FrameClock? = null
    
    // Estados de Interfaz
    private var statusMsg by mutableStateOf("Listo · Elegí una foto")
    private var textOverlay by mutableStateOf("") // Nuevo estado para el Texto
    private var isPlaying by mutableStateOf(false)
    private var showExportBtn by mutableStateOf(false)

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> 
        if (uri != null) loadPhoto(uri) 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        poseDetector = PoseDetector(this)

        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize().background(Ink)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        
                        // Barra Superior
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("JEDL Studio", color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Proyecto sin título", color = TextGray, fontSize = 12.sp)
                            }
                            if (showExportBtn) {
                                Button(
                                    onClick = { exportVideo() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Export", tint = Ink, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exportar", color = Ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Escenario (SurfaceView)
                        Box(
                            modifier = Modifier.weight(1f).padding(16.dp).border(1.dp, Panel, RoundedCornerShape(12.dp)).background(ComposeColor.Black, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceView(ctx).also { surface ->
                                        previewSurface = surface
                                        surface.holder.addCallback(object : SurfaceHolder.Callback {
                                            override fun surfaceCreated(h: SurfaceHolder) { drawPreview() }
                                            override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) { drawPreview() }
                                            override fun surfaceDestroyed(h: SurfaceHolder) {}
                                        })
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            
                            if (photo == null) {
                                Text("Toca el botón '+' para cargar una foto", color = ComposeColor.Gray, fontSize = 14.sp)
                            }
                        }

                        // Mensaje de Estado
                        Text(
                            text = statusMsg,
                            color = ComposeColor.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontSize = 12.sp
                        )

                        // Panel de Texto (Aparece cuando hay foto)
                        if (photo != null) {
                            OutlinedTextField(
                                value = textOverlay,
                                onValueChange = { 
                                    textOverlay = it
                                    drawPreview() 
                                },
                                label = { Text("Escribe un subtítulo...", color = TextGray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Gold,
                                    unfocusedBorderColor = Panel,
                                    focusedTextColor = ComposeColor.White,
                                    unfocusedTextColor = ComposeColor.White
                                ),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Barra de Herramientas
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Panel).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ToolItem("Fondo") { Toast.makeText(this@MainActivity, "Próximamente", Toast.LENGTH_SHORT).show() }
                            ToolItem("Actor") { pickLauncher.launch("image/*") }
                            ToolItem("Texto") { Toast.makeText(this@MainActivity, "Escribe en el cuadro de arriba", Toast.LENGTH_SHORT).show() }
                            ToolItem("Audio") { Toast.makeText(this@MainActivity, "Próximamente", Toast.LENGTH_SHORT).show() }
                            ToolItem("Estilo") { Toast.makeText(this@MainActivity, "Próximamente", Toast.LENGTH_SHORT).show() }
                        }

                        // Línea de tiempo y Play
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Ink).padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { startAnimation() },
                                modifier = Modifier.size(48.dp).background(Gold, RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Ink, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Box(
                                modifier = Modifier.weight(1f).height(40.dp).background(Panel, RoundedCornerShape(8.dp)).border(1.dp, ComposeColor.DarkGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animProgress).background(Gold.copy(alpha = 0.3f)))
                                Text("0:05", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Botón flotante
                    FloatingActionButton(
                        onClick = { pickLauncher.launch("image/*") },
                        containerColor = Gold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Ink)
                    }
                }
            }
        }
    }

    @Composable
    fun ToolItem(name: String, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
            Box(modifier = Modifier.size(40.dp).background(ComposeColor(0xFF1B1D24), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("+", color = Gold, fontSize = 20.sp)
            }
            Text(name, color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }

    private fun loadPhoto(uri: Uri) {
        statusMsg = "Detectando juntas..."
        Thread {
            val bitmap = try { contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } } catch (e: Exception) { null }
            if (bitmap == null) { runOnUiThread { statusMsg = "Error al cargar foto" }; return@Thread }
            val detected = try { poseDetector.detectJoints(bitmap) } catch (e: Exception) { emptyList() }
            runOnUiThread {
                photo = bitmap; joints = detected; animProgress = 0f
                statusMsg = "Foto cargada · ${detected.size} juntas detectadas"
                showExportBtn = true
                drawPreview()
            }
        }.start()
    }

    private fun startAnimation() {
        if (photo == null) { statusMsg = "Elegí una foto primero"; return }
        // Arreglo 2: Unificamos a 5 segundos exactos (5f * 30fps)
        frameClock?.stop(); animProgress = 0f; statusMsg = "Animando (5 segundos)..."; isPlaying = true
        val clock = FrameClock(fps = 30) { frame ->
            val p = min(1f, frame / (30f * 5f))
            animProgress = p; runOnUiThread { drawPreview() }
            if (p >= 1f) runOnUiThread { statusMsg = "Listo para exportar"; isPlaying = false }
        }
        frameClock = clock; clock.start()
    }

    private fun exportVideo() {
        if (photo == null) { statusMsg = "Elegí una foto primero"; return }
        statusMsg = "Exportando a Galería..."
        
        val bmp = photo!!; val jts = joints; val txt = textOverlay
        Thread {
            try {
                val tempFile = File(cacheDir, "temp_export.mp4")
                val exporter = Mp4Exporter(720, 1280, 30, 5f)
                
                // Arreglo 1: En el video final NO dibujamos el esqueleto (drawSkeleton = false)
                val ok = exporter.export(tempFile.absolutePath, { pct -> runOnUiThread { statusMsg = "Exportando… $pct%" } }) { progress -> 
                    renderSceneToBitmap(bmp, jts, txt, progress, 720, 1280, drawSkeleton = false) 
                }

                if (ok) {
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
                            tempFile.inputStream().use { input -> input.copyTo(out) }
                        }
                        runOnUiThread { statusMsg = "✓ ¡Video guardado en tu Galería!" }
                    }
                }
            } catch (e: Exception) { runOnUiThread { statusMsg = "Error: ${e.message}" } }
        }.start()
    }

    private fun drawPreview() {
        val holder = previewSurface?.holder ?: return
        val canvas = holder.lockCanvas() ?: return
        try { 
            // En la vista previa SÍ dibujamos el esqueleto (drawSkeleton = true)
            renderScene(canvas, canvas.width, canvas.height, animProgress, photo, joints, textOverlay, drawSkeleton = true) 
        } finally { holder.unlockCanvasAndPost(canvas) }
    }

    private fun renderScene(
        canvas: Canvas, w: Int, h: Int, progress: Float, 
        bmp: Bitmap?, jts: List<PointF>, text: String, drawSkeleton: Boolean
    ) {
        canvas.drawColor(Color.BLACK)
        bmp?.let {
            val bounce = 1.0f + (sin(progress * Math.PI * 6).toFloat() * 0.05f)
            val baseScale = min(w.toFloat() / it.width, h.toFloat() / it.height) * 0.85f
            val scale = baseScale * bounce
            val dw = it.width * scale; val dh = it.height * scale
            val dx = (w - dw) / 2f; val dy = (h - dh) / 2f
            canvas.drawBitmap(it, null, RectF(dx, dy, dx + dw, dy + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            
            // Dibujar Texto (Nuevo)
            if (text.isNotEmpty()) {
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 60f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                    setShadowLayer(8f, 4f, 4f, Color.BLACK) // Sombra para que se lea
                }
                // Dibujamos el texto en la parte inferior del escenario
                canvas.drawText(text, w / 2f, h * 0.9f, textPaint)
            }

            // Arreglo 1: Solo dibujar esqueleto si drawSkeleton es true
            if (drawSkeleton) {
                val scaledJoints = jts.map { PointF(dx + (it.x / bmp.width) * dw, dy + (it.y / bmp.height) * dh) }
                val skeletonPaint = Paint().apply { color = Color.parseColor("#E87722"); strokeWidth = 5f; style = Paint.Style.STROKE }
                val connections = listOf(Pair(11, 12), Pair(11, 13), Pair(13, 15), Pair(12, 14), Pair(14, 16), Pair(11, 23), Pair(12, 24), Pair(23, 24), Pair(23, 25), Pair(25, 27), Pair(24, 26), Pair(26, 28))
                
                for ((i, j) in connections) {
                    if (i < scaledJoints.size && j < scaledJoints.size) canvas.drawLine(scaledJoints[i].x, scaledJoints[i].y, scaledJoints[j].x, scaledJoints[j].y, skeletonPaint)
                }
                for (j in scaledJoints) canvas.drawCircle(j.x, j.y, 6f, Paint().apply { color = Color.RED })
            }
        }
    }

    private fun renderSceneToBitmap(bmp: Bitmap, jts: List<PointF>, txt: String, progress: Float, w: Int, h: Int, drawSkeleton: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        renderScene(Canvas(out), w, h, progress, bmp, jts, txt, drawSkeleton)
        return out
    }
}
