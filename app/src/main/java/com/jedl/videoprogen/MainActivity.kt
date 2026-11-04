package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    private var backgroundPhoto: Bitmap? = null
    private var skin: PuppetSkin? = null

    @Volatile private var animProgress: Float = 0f
    private var frameClock: FrameClock? = null
    private var blendClock: FrameClock? = null

    private var statusMsg by mutableStateOf("Listo · Elegí una foto")
    private var textOverlay by mutableStateOf("")
    private var template by mutableStateOf("idle")
    private var templatePrev by mutableStateOf("idle")
    private var blendStartMs by mutableStateOf(0L)
    private var showExportBtn by mutableStateOf(false)
    private var showSheet by mutableStateOf(false)

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

                        // Panel de Texto (subtítulo)
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
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ToolItem("Fondo") { bgLauncher.launch("image/*") }
                            ToolItem("Actor") { pickLauncher.launch("image/*") }
                            ToolItem("Texto") { Toast.makeText(this@MainActivity, "Escribe arriba", Toast.LENGTH_SHORT).show() }
                            ToolItem("Movimiento") { showSheet = true }
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

                    // Hoja inferior: Plantillas de Movimiento
                    if (showSheet) MovementSheet()
                }
            }
        }
    }

    // ---------- HOJA INFERIOR: PLANTILLAS DE MOVIMIENTO ----------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MovementSheet() {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Panel
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Plantillas de Movimiento", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Elegí cómo se mueve tu muñeco", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MovementOption("Reposo", "idle", Modifier.weight(1f))
                    MovementOption("Caminar", "walk", Modifier.weight(1f))
                    MovementOption("Saludar", "wave", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    @Composable
    fun MovementOption(label: String, key: String, modifier: Modifier = Modifier) {
        val selected = template == key
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .background(if (selected) ComposeColor(0xFF231E12) else ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                .border(1.dp, if (selected) Gold else ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                .clickable { selectTemplate(key); showSheet = false }
                .padding(vertical = 12.dp)
        ) {
            MiniPuppet(key)
            Text(label, color = if (selected) Gold else TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }

    // Mini-muñeco dibujado a mano para cada plantilla (sin iconos externos)
    @Composable
    fun MiniPuppet(kind: String) {
        Canvas(modifier = Modifier.size(width = 56.dp, height = 64.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val gold = Gold
            val body = ComposeColor(0xFF3A3E46)
            val lineW = h * 0.11f
            val headR = h * 0.13f
            val headCy = h * 0.15f + headR
            val neckY = headCy + headR
            val hipY = h * 0.60f
            val armY = neckY + h * 0.28f

            drawCircle(gold, headR, Offset(cx, headCy), style = Stroke(2.5f))
            drawCircle(body, headR, Offset(cx, headCy))
            drawLine(body, Offset(cx, neckY), Offset(cx, hipY), lineW, StrokeCap.Round)

            when (kind) {
                "walk" -> {
                    drawLine(body, Offset(cx, hipY), Offset(cx - w * 0.30f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, hipY), Offset(cx + w * 0.30f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx - w * 0.26f, armY + h * 0.06f), lineW * 0.8f, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx + w * 0.26f, armY - h * 0.06f), lineW * 0.8f, StrokeCap.Round)
                }
                "wave" -> {
                    drawLine(body, Offset(cx, hipY), Offset(cx - w * 0.16f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, hipY), Offset(cx + w * 0.16f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx - w * 0.24f, armY), lineW * 0.8f, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx + w * 0.32f, headCy + h * 0.02f), lineW * 0.8f, StrokeCap.Round)
                }
                else -> { // reposo
                    drawLine(body, Offset(cx, hipY), Offset(cx - w * 0.16f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, hipY), Offset(cx + w * 0.16f, h * 0.92f), lineW, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx - w * 0.24f, armY), lineW * 0.8f, StrokeCap.Round)
                    drawLine(body, Offset(cx, neckY + 4f), Offset(cx + w * 0.24f, armY), lineW * 0.8f, StrokeCap.Round)
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

    // ---------- PLANTILLAS: cambio suave (0.5 s de transición) ----------
    private fun labelFor(t: String): String = when (t) {
        "walk" -> "Caminar"
        "wave" -> "Saludar"
        else -> "Reposo"
    }

    private fun selectTemplate(t: String) {
        if (t == template) return
        templatePrev = template
        template = t
        blendStartMs = SystemClock.elapsedRealtime()
        statusMsg = "Plantilla: ${labelFor(t)}"
        runBlendPreview()
    }

    private fun runBlendPreview() {
        blendClock?.stop()
        val clock = FrameClock(30) { frame ->
            drawPreview()
            if (frame >= 15L) blendClock?.stop()
        }
        blendClock = clock
        clock.start()
    }

    private fun blendFactor(): Float {
        if (template == templatePrev) return 1f
        return ((SystemClock.elapsedRealtime() - blendStartMs) / 450f).coerceIn(0f, 1f)
    }

    // ---------- CARGA DE FOTOS ----------
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
            // INTERCAMBIO DE TEXTURA: la cara y el torso se pegan al muñeco
            val newSkin = if (cut != null && detected.size >= 29) {
                try { PuppetSkin(cut, detected) } catch (e: Exception) { null }
            } else null
            runOnUiThread {
                photo = bitmap
                skin = newSkin
                animProgress = 0f
                showExportBtn = true
                statusMsg = if (newSkin != null) {
                    "Foto cargada · tu cara y torso pegados al muñeco"
                } else {
                    "Foto cargada · muñeco simple (la persona no se detectó completa)"
                }
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

    // ---------- ANIMACIÓN Y EXPORT ----------
    private fun startAnimation() {
        frameClock?.stop(); animProgress = 0f; statusMsg = "Animando (5 segundos)..."
        val clock = FrameClock(fps = 30) { frame ->
            val p = Math.min(1f, frame / (30f * 5f))
            animProgress = p; drawPreview()
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
        ExportService.start(this, bmp, backgroundPhoto, skin, textOverlay, template)
    }

    private fun drawPreview() {
        val holder = previewSurface?.holder ?: return
        val canvas = holder.lockCanvas() ?: return
        try {
            SceneRenderer.renderScene(
                canvas, canvas.width, canvas.height, animProgress,
                backgroundPhoto, skin, textOverlay,
                template, templatePrev, blendFactor(),
                drawSkeleton = true
            )
        } finally { holder.unlockCanvasAndPost(canvas) }
    }
}
