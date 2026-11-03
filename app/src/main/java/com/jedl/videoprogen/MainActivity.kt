package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val Ink = ComposeColor(0xFF0B0C10)
val Panel = ComposeColor(0xFF14151A)
val Gold = ComposeColor(0xFFF5B841)
val TextGray = ComposeColor(0xFFE7E8EE)

class MainActivity : ComponentActivity() {
    private var previewSurface: SurfaceView? = null
    private lateinit var poseDetector: PoseDetector
    private var segmenter: SubjectSegmenter? = null
    private var photo: Bitmap? = null
    private var subject: Bitmap? = null
    private var backgroundPhoto: Bitmap? = null
    private var pieces: List<Piece>? = null
    private var joints: List<android.graphics.PointF> = emptyList()

    @Volatile private var animProgress: Float = 0f
    private var frameClock: FrameClock? = null

    private var statusMsg by mutableStateOf("Listo · Elegí una foto")
    private var textOverlay by mutableStateOf("")
    private var template by mutableStateOf("idle")
    private var showExportBtn by mutableStateOf(false)
    private var hasCutout by mutableStateOf(false)

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadPhoto(uri)
    }
    private val bgLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadBackground(uri)
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
                                    onClick = { triggerExportService() },
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

                        // Panel de Texto + Plantillas
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

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TemplateChip("Idle", template == "idle") { template = "idle"; drawPreview() }
                                TemplateChip("Saludar", template == "wave") { template = "wave"; drawPreview() }
                                TemplateChip("Caminar", template == "walk") { template = "walk"; drawPreview() }
                                if (hasCutout) Text("· capas activas", color = TextGray, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }

                        // Barra de Herramientas
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Panel).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolItem("Fondo") { bgLauncher.launch("image/*") }
                            ToolItem("Actor") { pickLauncher.launch("image/*") }
                            ToolItem("Texto") { Toast.makeText(this@MainActivity, "Escribe arriba", Toast.LENGTH_SHORT).show() }
                            ToolItem("Audio") { Toast.makeText(this@MainActivity, "Próximamente (v0.3.1)", Toast.LENGTH_SHORT).show() }
                            ToolItem("Estilo") { Toast.makeText(this@MainActivity, "Próximamente (v0.3.2)", Toast.LENGTH_SHORT).show() }
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

    @Composable
    fun TemplateChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .background(if (selected) Gold else Panel, RoundedCornerShape(50))
                .border(1.dp, if (selected) Gold else ComposeColor.DarkGray, RoundedCornerShape(50))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(label, color = if (selected) Ink else TextGray, fontSize = 12.sp)
        }
    }

    // Decodifica una imagen limitando su lado mayor (RAM segura para 4 GB)
    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while ((bounds.outWidth / sample > maxDim) || (bounds.outHeight / sample > maxDim)) sample *= 2
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) { null }
    }

    private fun loadPhoto(uri: Uri) {
        statusMsg = "Analizando foto (juntas + recorte)..."
        Thread {
            val bitmap = decodeScaled(uri, 1600)
            if (bitmap == null) { runOnUiThread { statusMsg = "Error al cargar foto" }; return@Thread }
            val detected = try { poseDetector.detectJoints(bitmap) } catch (e: Exception) { emptyList() }
            if (segmenter == null) segmenter = try { SubjectSegmenter(this@MainActivity) } catch (e: Exception) { null }
            val cut = try { segmenter?.cutSubject(bitmap) } catch (e: Exception) { null }
            val pcs = if (cut != null && detected.size >= 29) CutoutAnimator.buildPieces(cut, detected) else null
            runOnUiThread {
                photo = bitmap; joints = detected; subject = cut; pieces = pcs
                animProgress = 0f
                showExportBtn = true
                hasCutout = pcs != null
                val cutMsg = if (pcs != null) " · persona en 5 capas" else if (cut != null) " · persona recortada" else ""
                statusMsg = "Foto cargada · ${detected.size} juntas$cutMsg"
                drawPreview()
            }
        }.start()
    }

    private fun loadBackground(uri: Uri) {
        Thread {
            val bmp = decodeScaled(uri, 1280)
            if (bmp != null) {
                backgroundPhoto = bmp
                runOnUiThread { statusMsg = "Fondo cargado"; drawPreview() }
            } else {
                runOnUiThread { statusMsg = "Error al cargar fondo" }
            }
        }.start()
    }

    private fun startAnimation() {
        if (photo == null) { statusMsg = "Elegí una foto primero"; return }
        frameClock?.stop(); animProgress = 0f; statusMsg = "Animando (5 segundos)..."
        val clock = FrameClock(fps = 30) { frame ->
            val p = Math.min(1f, frame / (30f * 5f))
            animProgress = p; runOnUiThread { drawPreview() }
            if (p >= 1f) runOnUiThread { statusMsg = "Listo para exportar" }
        }
        frameClock = clock; clock.start()
    }

    // El export corre en el servicio en primer plano (anti-MIUI)
    private fun triggerExportService() {
        if (photo == null) { statusMsg = "Elegí una foto primero"; return }
        val bmp = photo!!
        statusMsg = "Iniciando exportación en 1er plano..."
        ExportService.onStatus = { msg -> runOnUiThread { statusMsg = msg } }
        ExportService.start(this, bmp, subject, backgroundPhoto, pieces, joints, textOverlay, template)
    }

    private fun drawPreview() {
        val holder = previewSurface?.holder ?: return
        val canvas = holder.lockCanvas() ?: return
        try {
            SceneRenderer.renderScene(canvas, canvas.width, canvas.height, animProgress, photo, subject, backgroundPhoto, pieces, joints, textOverlay, template, drawSkeleton = true)
        } finally { holder.unlockCanvasAndPost(canvas) }
    }
}
