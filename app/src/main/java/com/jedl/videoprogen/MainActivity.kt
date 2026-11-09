package com.jedl.videoprogen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.MediaMetadataRetriever
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

val Ink = ComposeColor(0xFF0B0C10)
val Panel = ComposeColor(0xFF14151A)
val Gold = ComposeColor(0xFFF5B841)
val TextGray = ComposeColor(0xFFE7E8EE)

// Formatos de exportación disponibles (el usuario elige al exportar)
data class VideoFormat(val label: String, val w: Int, val h: Int)

class MainActivity : ComponentActivity() {
    private var previewSurface: SurfaceView? = null
    private var photo: Bitmap? = null
    private var originalPhoto: Bitmap? = null   // foto sin recortar (para reintentar el recorte)
    private var backgroundPhoto: Bitmap? = null
    private var mesh: SkinnedMesh? = null

    // VOZ (PASO 4): gestor de TextToSpeech, listo desde onCreate
    private var ttsManager: TtsManager? = null

    // DESHACER: pila de estados (foto+malla) antes de cada cambio.
    // Máximo 4 estados para no agotar la RAM de 4 GB.
    // v0.5.1 (FASE A): el Snap guarda también el FONDO (el estilo se puede
    // aplicar al fondo y hay que poder deshacerlo) y existe pila de REHACER.
    private class Snap(val photo: Bitmap?, val mesh: SkinnedMesh?, val background: Bitmap?)
    private val undoStack = ArrayDeque<Snap>()
    private val redoStack = ArrayDeque<Snap>()

    @Volatile private var animProgress: Float = 0f
    private var frameClock: FrameClock? = null
    private var blendClock: FrameClock? = null

    private var statusMsg by mutableStateOf("Listo · Elegí una foto")
    private var textOverlay by mutableStateOf("")
    // TEXTO POSICIONABLE (TAREA 4): textX/textY en proporción del lienzo + tamaño
    private var textX by mutableStateOf(0.5f)
    private var textY by mutableStateOf(0.9f)
    private var textSizePx by mutableStateOf(60f)
    // v0.5.0: la plantilla activa puede ser builtin (Reposo/Caminar/Saludar),
    // una plantilla CLONADA DE VIDEO (mocap IA offline) o una creada a mano
    // con el editor de poses (IK).
    private var template by mutableStateOf(ActiveTemplate.of("idle"))
    private var templatePrev by mutableStateOf(ActiveTemplate.of("idle"))
    // Plantillas creadas por el usuario (mocap de video + poses guardadas)
    private val userTemplates = mutableStateListOf<MotionTemplate>()
    // v0.8.0: acciones de la BIBLIOTECA de movimientos (assets/motions):
    // pares (id, label) cargados al abrir la app, listos para la hoja
    // de Movimiento. Los ids que coinciden con un builtin PISAN su
    // fórmula con los keyframes del archivo.
    private val libraryActions = mutableStateListOf<Pair<String, String>>()
    // MODO POSAR (IK): arrastrar la mano o el pie del muñeco con el dedo
    private var poseMode by mutableStateOf(false)
    private var customPose: Pose? = null
    private val editingFrames = ArrayList<PoseFrame>()
    private var activeLimb: Int = 0   // 0=ninguno 1-4=manos/pies(IK) 5-8=codos/rodillas
    // Dimensiones del último render (para el hit-test del IK en pantalla)
    @Volatile private var lastCanvasW = 0
    @Volatile private var lastCanvasH = 0
    private var blendStartMs by mutableStateOf(0L)
    private var showExportBtn by mutableStateOf(false)
    private var showSheet by mutableStateOf(false)
    private var showFormatSheet by mutableStateOf(false)
    private var showStyleSheet by mutableStateOf(false)
    private var canUndo by mutableStateOf(false)
    private var canRedo by mutableStateOf(false)
    // v0.5.1 (FASE A): posición libre del actor (proporción del lienzo).
    // Arrastrás el muñeco en el escenario y queda donde lo sueltes.
    // Preview y export usan ESTOS mismos valores: lo que ves es lo que sale.
    private var actorX by mutableStateOf(0.5f)
    private var actorY by mutableStateOf(0.55f)
    // v0.5.1 (FASE A): zoom del editor de poses (1x..4x) para posar con
    // precisión con el dedo (pedido del dueño: ZOOM+ / ZOOM-).
    private var poseZoom by mutableStateOf(1f)
    // v0.6.0 (FASE B): PERSONAJE POR PIEZAS + cara recortada de la foto.
    // avatarMode = el actor es el personaje dibujado (sin distorsión);
    // el editor grande son VARIANTES (colores/peinado/barba) del AvatarSpec.
    private var avatarMode by mutableStateOf(false)
    private var avatarSpec by mutableStateOf(Avatars.presets()[0])
    private var showAvatarSheet by mutableStateOf(false)
    // Cara: dibujada por código o la FOTO RECORTADA (FaceCutter IA /
    // elipse manual con el dedo). El recorte pasa a ser SOLO CARA.
    private var faceFromPhoto by mutableStateOf(false)
    private var faceBitmap: Bitmap? = null
    private var faceEditMode by mutableStateOf(false)   // colocar la elipse con el dedo
    private var faceCx by mutableStateOf(0.5f)          // centro en fracciones de la foto
    private var faceCy by mutableStateOf(0.35f)
    private var faceR by mutableStateOf(0.18f)         // radio (lado menor de la foto)
    @Volatile private var faceFitL = 0f                // rect de la foto en pantalla
    @Volatile private var faceFitT = 0f
    @Volatile private var faceFitW = 0f
    @Volatile private var faceFitH = 0f
    // Dirección del caminar/correr (pedido del dueño: ver todas)
    private var direction by mutableStateOf("der")

    private val pickLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadPhoto(uri)
    }
    private val bgLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) loadBackground(uri)
    }
    // ETAPA C: selector de VIDEO para clonar el movimiento (mocap IA offline)
    private val videoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) cloneFromVideo(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TTS inicializado aqui: onInit es asincrono y asi ya esta listo
        // cuando el usuario toca el boton "Voz" por primera vez
        ttsManager = TtsManager(this)

        // v0.8.0: cargar la BIBLIOTECA de movimientos (assets/motions) en
        // hilo de fondo. Cada acción inyectada PISA su fórmula en
        // PuppetEngine.poseFor() con los keyframes del archivo.
        Thread {
            val n = PuppetEngine.loadLibrary(assets, "motions")
            runOnUiThread {
                libraryActions.clear()
                for (id in PuppetEngine.libraryActionIds()) {
                    libraryActions.add(Pair(id, PuppetEngine.labelForAction(id) ?: id))
                }
                statusMsg = if (n > 0) "Biblioteca: " + n + " acciones cargadas"
                             else "Sin biblioteca de movimientos · fórmulas internas"
            }
        }.start()

        setContent {
            val focusManager = LocalFocusManager.current
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
                                    onClick = { if (photo != null) showFormatSheet = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Export", tint = Ink, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Exportar", color = Ink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Escenario (SurfaceView). Tocar el escenario cierra el
                        // teclado: el subtitulo queda escrito en pantalla y el
                        // campo deja de estar activo.
                        Box(
                            modifier = Modifier.weight(1f).padding(16.dp)
                                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
                                // v0.5.1 (FASE A): UN solo gesto de arrastre.
                                // - MODO POSAR: arrastra los PUNTOS del muñeco (IK).
                                // - MODO NORMAL: arrastra el ACTOR y colócalo donde
                                //   quieras en la escena (ya no vive fijo al centro).
                                .pointerInput(poseMode, faceEditMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            if (faceEditMode) moveFaceEllipse(offset.x, offset.y)
                                            else if (poseMode) selectLimb(offset.x, offset.y)
                                        },
                                        onDrag = { change, dragAmount ->
                                            if (faceEditMode) {
                                                moveFaceEllipse(change.position.x, change.position.y)
                                            } else if (poseMode) {
                                                dragLimbTo(change.position.x, change.position.y)
                                            } else {
                                                // v0.6.0 FIX (reporte del dueño): dragAmount es
                                                // el DELTA del gesto; change.position es la posicion
                                                // absoluta del dedo (bug v0.5.1: sumaba la posicion
                                                // absoluta en cada evento y el actor se disparaba
                                                // a la derecha y no volvía al centro).
                                                if (lastCanvasW > 0 && lastCanvasH > 0 && (photo != null || avatarMode)) {
                                                    actorX = (actorX + dragAmount.x / lastCanvasW).coerceIn(0.05f, 0.95f)
                                                    actorY = (actorY + dragAmount.y / lastCanvasH).coerceIn(0.05f, 0.95f)
                                                    change.consume()
                                                    drawPreview()
                                                }
                                            }
                                        },
                                        onDragEnd = { activeLimb = 0 }
                                    )
                                }
                                .border(1.dp, Panel, RoundedCornerShape(12.dp))
                                .background(ComposeColor.Black, RoundedCornerShape(12.dp)),
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

                        // Panel de Texto (subtítulo) + A+ / A- (TAREA 4) + Deshacer
                        if (photo != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = textOverlay,
                                    onValueChange = {
                                        textOverlay = it
                                        drawPreview()   // fuerza el redibujado del SurfaceView
                                    },
                                    label = { Text("Escribe un subtítulo...", color = TextGray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Gold,
                                        unfocusedBorderColor = Panel,
                                        focusedTextColor = ComposeColor.White,
                                        unfocusedTextColor = ComposeColor.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // A+ : agrandar el texto
                                    IconButton(
                                        onClick = {
                                            textSizePx = (textSizePx + 8f).coerceAtMost(140f)
                                            drawPreview()
                                        },
                                        modifier = Modifier.size(36.dp).background(ComposeColor(0xFF1B1D24), RoundedCornerShape(8.dp))
                                    ) { Text("A+", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // A- : reducir el texto
                                    IconButton(
                                        onClick = {
                                            textSizePx = (textSizePx - 8f).coerceAtLeast(24f)
                                            drawPreview()
                                        },
                                        modifier = Modifier.size(36.dp).background(ComposeColor(0xFF1B1D24), RoundedCornerShape(8.dp))
                                    ) { Text("A-", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // DESHACER: vuelve al estado anterior (foto+malla+fondo)
                                    IconButton(
                                        onClick = { undoChange() },
                                        enabled = canUndo,
                                        modifier = Modifier.size(36.dp).background(ComposeColor(0xFF1B1D24), RoundedCornerShape(8.dp))
                                    ) { Icon(Icons.Default.Undo, contentDescription = "Deshacer", tint = Gold, modifier = Modifier.size(16.dp)) }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // v0.5.1 (FASE A): REHACER — si deshiciste por descuido,
                                    // volvés al progreso que tenías.
                                    IconButton(
                                        onClick = { redoChange() },
                                        enabled = canRedo,
                                        modifier = Modifier.size(36.dp).background(ComposeColor(0xFF1B1D24), RoundedCornerShape(8.dp))
                                    ) { Icon(Icons.Default.Redo, contentDescription = "Rehacer", tint = Gold, modifier = Modifier.size(16.dp)) }
                                }
                            }
                        }

                        // MODO POSAR (IK): fila de controles del editor de poses
                        // v0.6.0 (FASE B): edición de la elipse de la cara
                        if (faceEditMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { faceR = (faceR - 0.02f).coerceAtLeast(0.06f); drawPreview() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 34.dp)
                                ) { Text("-", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                                Text("cara " + (faceR * 100f).toInt() + "%", color = TextGray, fontSize = 11.sp)
                                Button(
                                    onClick = { faceR = (faceR + 0.02f).coerceAtMost(0.45f); drawPreview() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(width = 44.dp, height = 34.dp)
                                ) { Text("+", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                                Button(
                                    onClick = { confirmFaceCut() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Confirmar cara", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                Button(
                                    onClick = { faceEditMode = false; drawPreview() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp)
                                ) { Text("Salir", color = TextGray, fontSize = 12.sp) }
                            }
                        }
                        if (poseMode) {
                            // v0.5.1 (FASE A): ZOOM+ / ZOOM- para posar con precisión
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { poseZoom = (poseZoom + 0.5f).coerceAtMost(4f); drawPreview() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(width = 52.dp, height = 34.dp)
                                ) { Text("Z+", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                Text("zoom " + poseZoom + "x", color = TextGray, fontSize = 11.sp)
                                Button(
                                    onClick = { poseZoom = (poseZoom - 0.5f).coerceAtLeast(1f); drawPreview() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(width = 52.dp, height = 34.dp)
                                ) { Text("Z-", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { savePoseKeyframe() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Gold),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Guardar pose", color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                Button(
                                    onClick = { finishUserAnimation() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Terminar animación", color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                                Button(
                                    onClick = { exitPoseMode() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Panel),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Salir", color = TextGray, fontSize = 12.sp) }
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
                            ToolItem("Texto") { focusSubtitulo() }
                            ToolItem("Movimiento") { showSheet = true }
                            ToolItem("Recorte") { applyDualCutout() }
                            ToolItem("Estilo") { showStyleSheet = true }
                            // v0.6.0 (FASE B): editor de personaje por piezas
                            ToolItem("Personaje") { showAvatarSheet = true }
                            ToolItem("Voz") {
                                ttsManager?.speak(textOverlay)
                                if (textOverlay.isBlank()) {
                                    Toast.makeText(this@MainActivity, "Escribí un subtítulo primero", Toast.LENGTH_SHORT).show()
                                }
                            }
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

                    // Hoja inferior: Planillas de Estilo (PASO 3)
                    if (showStyleSheet) StyleSheet()

                    // Hoja inferior: Formato del video
                    if (showFormatSheet) FormatSheet()

                    // Hoja inferior: Personaje (FASE B)
                    if (showAvatarSheet) AvatarSheet()
                }
            }
        }
    }

    // El boton "Texto" ahora re-abre el teclado del subtitulo (el campo
    // ya no se queda pegado: tocar el escenario o "Listo" lo cierra).
    private fun focusSubtitulo() {
        Toast.makeText(this, "Toca el campo de subtítulo para escribir", Toast.LENGTH_SHORT).show()
    }

    // ---------- HOJA INFERIOR: PLANTILLAS DE MOVIMIENTO (v0.5.0) ----------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MovementSheet() {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Panel
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                Text("Plantillas de Movimiento", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Elegí cómo se mueve tu muñeco", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MovementOption("Reposo", "idle", Modifier.weight(1f))
                    MovementOption("Caminar", "walk", Modifier.weight(1f))
                    MovementOption("Saludar", "wave", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                // v0.6.0 (FASE B): 5 PLANTILLAS NUEVAS (8 en total)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MovementOption("Percusión", "drum", Modifier.weight(1f))
                    MovementOption("Correr", "run", Modifier.weight(1f))
                    MovementOption("Saltar", "jump", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MovementOption("Bailar", "dance", Modifier.weight(1f))
                    MovementOption("Celebrar", "cheer", Modifier.weight(1f))
                }
                // v0.8.0: acciones de la BIBLIOTECA (assets/motions). Las 8
                // primeras PISAN las fórmulas (walk.json manda sobre "walk");
                // el resto son acciones nuevas (Patada, Boxeo, Desfile...).
                if (libraryActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Biblioteca de movimientos (" + libraryActions.size + ")", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    for (i in libraryActions.indices step 3) {
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val last = minOf(i + 3, libraryActions.size)
                            for (j in i until last) {
                                MovementOption(libraryActions[j].second, libraryActions[j].first, Modifier.weight(1f))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                // v0.6.0 (FASE B): DIRECCIONES (pedido del dueño: ver TODAS al
                // caminar/correr; el perfil izquierdo es el espejo del derecho)
                Text("Dirección", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionOption("Perfil →", "der", Modifier.weight(1f))
                    DirectionOption("Perfil ←", "izq", Modifier.weight(1f))
                    DirectionOption("Frente", "frente", Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionOption("Espalda", "espalda", Modifier.weight(1f))
                    DirectionOption("Diagonal 3/4", "3/4", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(14.dp))

                // ETAPA C: clonar el movimiento de un video (mocap IA OFFLINE)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                        .border(1.dp, ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                        .clickable { showSheet = false; videoLauncher.launch("video/*") }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Clonar de un video (IA)", color = TextGray, fontSize = 14.sp)
                        Text("Graba un video moviéndote y tu muñeco repite el movimiento · 100% offline · primeros 5 s", color = ComposeColor.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("Elegir", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(14.dp))

                // MODO POSAR (IK): arrastrar mano/pie y guardar poses propias
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                        .border(1.dp, ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                        .clickable { showSheet = false; enterPoseMode() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Posar con el dedo (IK)", color = TextGray, fontSize = 14.sp)
                        Text("Arrastrá la mano o el pie del muñeco: codo y hombro se acomodan solos", color = ComposeColor.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("Entrar", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Plantillas creadas por el usuario (mocap + poses guardadas)
                if (userTemplates.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Mis plantillas", color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    for (mt in userTemplates) {
                        UserTemplateOption(mt)
                    }
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // ---------- HOJA INFERIOR: PERSONAJE (FASE B, v0.6.0) ----------
    // Editor GRANDE de variantes (pedido del dueño ya en Fase B): 3
    // personajes prototipo HOMBRE + colores/peinado/barba por categoría
    // + cara dibujada o la cara recortada de la foto + dirección.
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AvatarSheet() {
        ModalBottomSheet(
            onDismissRequest = { showAvatarSheet = false },
            containerColor = Panel
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Personaje por piezas", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("El personaje se dibuja pieza a pieza: se mueve con TODO (plantillas, IK, clonar vídeo) SIN distorsión", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ModeOpt("Foto recortada", !avatarMode) {
                        avatarMode = false; drawPreview()
                    }
                    ModeOpt("Personaje", avatarMode) {
                        avatarMode = true; showExportBtn = true; drawPreview()
                    }
                }
                if (avatarMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Personajes", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in Avatars.presets()) {
                            ModeOpt(p.name, p.name == avatarSpec.name) {
                                avatarSpec = p.copy(); drawPreview()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Cara", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeOpt("Dibujada", !faceFromPhoto) {
                            faceFromPhoto = false; drawPreview()
                        }
                        ModeOpt("De la foto", faceFromPhoto) {
                            if (faceBitmap != null) { faceFromPhoto = true; drawPreview() }
                            else cutFaceAutoFlow()
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { cutFaceAutoFlow() },
                            colors = ButtonDefaults.buttonColors(containerColor = Panel),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Recortar cara (IA)", color = Gold, fontSize = 11.sp) }
                        Button(
                            onClick = { showAvatarSheet = false; faceEditMode = true; drawPreview() },
                            colors = ButtonDefaults.buttonColors(containerColor = Panel),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Elipse con el dedo", color = Gold, fontSize = 11.sp) }
                    }
                    // Variantes por categoría (editor grande)
                    SwatchRow("Piel", Avatars.SKINS, avatarSpec.skin) { avatarSpec = avatarSpec.copy(skin = it); drawPreview() }
                    SwatchRow("Cabello", Avatars.HAIRS, avatarSpec.hair) { avatarSpec = avatarSpec.copy(hair = it); drawPreview() }
                    Text("Peinado", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((i, s) in Avatars.HAIR_STYLE_NAMES.withIndex()) {
                            ModeOpt(s, avatarSpec.hairStyle == i, small = true) {
                                avatarSpec = avatarSpec.copy(hairStyle = i); drawPreview()
                            }
                        }
                    }
                    SwatchRow("Camiseta", Avatars.SHIRTS, avatarSpec.shirt) { avatarSpec = avatarSpec.copy(shirt = it); drawPreview() }
                    SwatchRow("Pantalón", Avatars.PANTS, avatarSpec.pants) { avatarSpec = avatarSpec.copy(pants = it); drawPreview() }
                    SwatchRow("Zapatos", Avatars.SHOES, avatarSpec.shoes) { avatarSpec = avatarSpec.copy(shoes = it); drawPreview() }
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeOpt("Sin barba", !avatarSpec.beard) { avatarSpec = avatarSpec.copy(beard = false); drawPreview() }
                        ModeOpt("Con barba", avatarSpec.beard) { avatarSpec = avatarSpec.copy(beard = true); drawPreview() }
                    }
                    Text("La dirección del caminar se elige en Movimiento", color = ComposeColor.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 10.dp))
                }
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Botón/píldora seleccionable reutilizable del editor de personaje
    @Composable
    fun ModeOpt(label: String, selected: Boolean, small: Boolean = false, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = if (selected) Gold else Panel),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(if (small) 30.dp else 36.dp)
        ) {
            Text(
                label,
                fontSize = if (small) 10.sp else 11.sp,
                color = if (selected) Ink else TextGray,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }

    // Fila de muestras de color de una categoría (piel, camiseta, etc.)
    @Composable
    fun SwatchRow(label: String, colors: IntArray, current: Int, pick: (Int) -> Unit) {
        Text(label, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in colors) {
                Box(
                    modifier = Modifier.size(32.dp)
                        .background(ComposeColor(c), RoundedCornerShape(16.dp))
                        .border(
                            if (c == current) 3.dp else 1.dp,
                            if (c == current) Gold else ComposeColor(0xFF2A2D35),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { pick(c) }
                )
            }
        }
    }

    // Opción de dirección (perfil/frente/espalda/diagonal)
    @Composable
    fun DirectionOption(label: String, key: String, modifier: Modifier = Modifier) {
        val selected = (direction == key)
        Button(
            onClick = { direction = key; drawPreview() },
            colors = ButtonDefaults.buttonColors(containerColor = if (selected) Gold else Panel),
            shape = RoundedCornerShape(8.dp),
            modifier = modifier.height(34.dp)
        ) {
            Text(label, fontSize = 10.sp, color = if (selected) Ink else TextGray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }

    // PLAN A del recorte de cara: Face Landmarker (IA). Si no hay modelo
    // o no encuentra cara, cae SOLA al plan B (elipse manual).
    private fun cutFaceAutoFlow() {
        val original = originalPhoto ?: photo
        if (original == null) { statusMsg = "Cargá primero una foto con tu cara (botón Actor)"; return }
        showAvatarSheet = false
        statusMsg = "Buscando tu cara (IA)... esperá un momento"
        Thread {
            val t0 = SystemClock.elapsedRealtime()
            val face = FaceCutter.cutFaceAuto(this, original)
            val ms = SystemClock.elapsedRealtime() - t0
            runOnUiThread {
                if (face != null) {
                    safeRecycle(faceBitmap)
                    faceBitmap = face
                    faceFromPhoto = true
                    avatarMode = true
                    showExportBtn = true
                    statusMsg = "Cara recortada con IA (" + ms + " ms)"
                    drawPreview()
                } else {
                    statusMsg = "IA de cara no lista: ajustá la elipse con el dedo"
                    faceEditMode = true
                    drawPreview()
                }
            }
        }.start()
    }

    // PLAN B: confirmar la elipse manual y recortar la cara
    private fun confirmFaceCut() {
        val original = originalPhoto ?: photo
        if (original == null) {
            faceEditMode = false
            statusMsg = "No hay foto para recortar"
            drawPreview()
            return
        }
        statusMsg = "Recortando cara..."
        Thread {
            val face = FaceCutter.cutFaceManual(original, faceCx, faceCy, faceR)
            runOnUiThread {
                safeRecycle(faceBitmap)
                faceBitmap = face
                faceFromPhoto = true
                avatarMode = true
                showExportBtn = true
                faceEditMode = false
                statusMsg = "Cara recortada · el personaje ya la lleva puesta"
                drawPreview()
            }
        }.start()
    }

    // Mueve el centro de la elipse con el dedo (coords de pantalla -> fracción de la foto)
    private fun moveFaceEllipse(canvasX: Float, canvasY: Float) {
        if (faceFitW <= 0f || faceFitH <= 0f) return
        faceCx = ((canvasX - faceFitL) / faceFitW).coerceIn(0.02f, 0.98f)
        faceCy = ((canvasY - faceFitT) / faceFitH).coerceIn(0.02f, 0.98f)
        drawPreview()
    }

    // Preview del modo recorte de cara: la foto + la elipse dorada
    private fun drawFaceEditPreview(canvas: android.graphics.Canvas) {
        val bmp = originalPhoto ?: photo ?: return
        canvas.drawColor(android.graphics.Color.BLACK)
        val cw = canvas.width.toFloat()
        val ch = canvas.height.toFloat()
        val s = minOf(cw / bmp.width, ch / bmp.height) * 0.92f
        val dw = bmp.width * s
        val dh = bmp.height * s
        val left = (cw - dw) / 2f
        val top = (ch - dh) / 2f
        faceFitL = left; faceFitT = top; faceFitW = dw; faceFitH = dh
        val bp = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bmp, null, android.graphics.RectF(left, top, left + dw, top + dh), bp)
        val ecx = left + faceCx * dw
        val ecy = top + faceCy * dh
        val rx = faceR * minOf(dw, dh)
        val ry = rx * 1.15f
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.style = android.graphics.Paint.Style.STROKE
        p.color = 0xFFF5B841.toInt()
        p.strokeWidth = 3f
        canvas.drawOval(android.graphics.RectF(ecx - rx, ecy - ry, ecx + rx, ecy + ry), p)
    }

    // ---------- HOJA INFERIOR: PLANILLAS DE ESTILO (PASO 3) ----------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun StyleSheet() {
        ModalBottomSheet(
            onDismissRequest = { showStyleSheet = false },
            containerColor = Panel
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Planillas de Estilo", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Elegí el filtro de tu foto", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(14.dp))
                StyleOption("Anime (IA)", "CARTOON")
                StyleOption("Cómic", "COMIC")
                StyleOption("Blanco y negro", "BW")
                // v0.5.1 (FASE A): el estilo también al FONDO (pedido del dueño:
                // actor y fondo en el mismo look de anime = escena coherente).
                StyleOption("Anime (IA) al FONDO", "CARTOON_BG")
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    @Composable
    fun StyleOption(label: String, key: String) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                .border(1.dp, ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                .clickable {
                    showStyleSheet = false
                    applyStyle(key)
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = TextGray, fontSize = 14.sp)
            Text("Aplicar", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    // ---------- HOJA INFERIOR: FORMATO DEL VIDEO ----------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun FormatSheet() {
        ModalBottomSheet(
            onDismissRequest = { showFormatSheet = false },
            containerColor = Panel
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Formato del video", color = Gold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Elegí las dimensiones del MP4", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(14.dp))
                FormatOption(VideoFormat("16:9 · Horizontal", 1280, 720))
                FormatOption(VideoFormat("9:16 · Vertical", 720, 1280))
                FormatOption(VideoFormat("1:1 · Cuadrado", 720, 720))
                FormatOption(VideoFormat("4:5 · Retrato", 720, 900))
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    @Composable
    fun FormatOption(f: VideoFormat) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                .border(1.dp, ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                .clickable {
                    showFormatSheet = false
                    triggerExportService(f.w, f.h)
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(f.label, color = TextGray, fontSize = 14.sp)
            Text("" + f.w + " × " + f.h, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }

    @Composable
    fun MovementOption(label: String, key: String, modifier: Modifier = Modifier) {
        val selected = (template.mocap == null && template.builtin == key)
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

    // Opción de una plantilla creada por el usuario (mocap o poses)
    @Composable
    fun UserTemplateOption(mt: MotionTemplate) {
        val selected = (template.mocap === mt)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(if (selected) ComposeColor(0xFF231E12) else ComposeColor(0xFF1B1D24), RoundedCornerShape(12.dp))
                .border(1.dp, if (selected) Gold else ComposeColor(0xFF2A2D35), RoundedCornerShape(12.dp))
                .clickable { selectActiveTemplate(ActiveTemplate(null, mt)); showSheet = false }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(mt.name, color = if (selected) Gold else TextGray, fontSize = 14.sp)
                Text(mt.frames.size.toString() + " poses · tocá para aplicar", color = ComposeColor.Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text("Aplicar", color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
    private fun labelFor(t: String): String {
        // v0.8.0: la acción puede venir de la BIBLIOTECA de movimientos
        PuppetEngine.labelForAction(t)?.let { return it }
        return when (t) {
            "walk" -> "Caminar"
            "wave" -> "Saludar"
            "drum" -> "Percusión"
            "run" -> "Correr"
            "jump" -> "Saltar"
            "dance" -> "Bailar"
            "cheer" -> "Celebrar"
            else -> "Reposo"
        }
    }

    private fun selectTemplate(t: String) {
        selectActiveTemplate(ActiveTemplate.of(t))
    }

    // Selección unificada: builtin, mocap de video o animación propia.
    private fun selectActiveTemplate(at: ActiveTemplate) {
        if (at == template) return
        templatePrev = template
        template = at
        blendStartMs = SystemClock.elapsedRealtime()
        val name = if (at.mocap != null) at.mocap.name else labelFor(at.builtin ?: "idle")
        statusMsg = "Plantilla: " + name
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

    // ---------- OPTIMIZACIÓN DE MEMORIA (TAREA 5) ----------
    // Decodifica una imagen limitando su lado mayor con inSampleSize
    // (potencia de 2). Nada supera 2048 px: RAM segura para 4 GB.
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

    // Libera un bitmap solo si nadie lo sigue usando (preview, malla,
    // export pendiente o estados guardados para deshacer/rehacer)
    private fun safeRecycle(b: Bitmap?) {
        if (b == null || b.isRecycled) return
        if (ExportService.pendingPhoto === b) return
        if (ExportService.pendingBackground === b) return
        val pm = ExportService.pendingMesh
        if (pm != null && pm.photo === b) return
        for (s in undoStack) if (s.photo === b || s.background === b) return
        for (s in redoStack) if (s.photo === b || s.background === b) return
        try { b.recycle() } catch (_: Exception) {}
    }

    // ---------- DESHACER / REHACER (v0.4.8 + v0.5.1) ----------
    // Guarda el estado actual (foto+malla+fondo) antes de recorte/estilo/carga.
    // Máximo 4 estados: el más viejo se libera para no agotar la RAM.
    private fun pushUndo() {
        // Al hacer un CAMBIO NUEVO, la rama de rehacer queda obsoleta.
        clearRedo()
        undoStack.addLast(Snap(photo, mesh, backgroundPhoto))
        while (undoStack.size > 4) {
            val old = undoStack.removeFirst()
            val oldBmp = old.photo
            if (oldBmp != null && oldBmp !== originalPhoto && oldBmp !== photo) {
                if (ExportService.pendingPhoto !== oldBmp) {
                    val pm = ExportService.pendingMesh
                    if (pm == null || pm.photo !== oldBmp) {
                        try { oldBmp.recycle() } catch (_: Exception) {}
                    }
                }
            }
            if (old.background != null && old.background !== backgroundPhoto) safeRecycle(old.background)
        }
        canUndo = true
    }

    // Vacía la pila de rehacer liberando lo que ya nadie usa
    private fun clearRedo() {
        for (s in redoStack) {
            if (s.photo != null && s.photo !== photo && s.photo !== originalPhoto) safeRecycle(s.photo)
            if (s.background != null && s.background !== backgroundPhoto) safeRecycle(s.background)
        }
        redoStack.clear()
        canRedo = false
    }

    private fun undoChange() {
        val snap = undoStack.removeLastOrNull() ?: return
        // El estado ACTUAL pasa a la pila de rehacer (para "adelante")
        redoStack.addLast(Snap(photo, mesh, backgroundPhoto))
        while (redoStack.size > 4) { redoStack.removeFirst() }
        val cur = photo
        if (cur !== snap.photo && cur !== originalPhoto) safeRecycle(cur)
        photo = snap.photo
        mesh = snap.mesh
        backgroundPhoto = snap.background
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        statusMsg = "Cambio deshecho"
        drawPreview()
    }

    // v0.5.1 (FASE A): "adelante" — recuperar el progreso tras un atrás
    private fun redoChange() {
        val snap = redoStack.removeLastOrNull() ?: return
        // El estado ACTUAL vuelve a la pila de deshacer (sin limpiar redo)
        undoStack.addLast(Snap(photo, mesh, backgroundPhoto))
        val cur = photo
        if (cur !== snap.photo && cur !== originalPhoto) safeRecycle(cur)
        photo = snap.photo
        mesh = snap.mesh
        backgroundPhoto = snap.background
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
        statusMsg = "Cambio rehecho"
        drawPreview()
    }

    // ---------- CARGA DE FOTOS ----------
    private fun loadPhoto(uri: Uri) {
        statusMsg = "Cargando foto..."
        Thread {
            val bitmap = decodeScaled(uri, 1600)
            if (bitmap == null) { runOnUiThread { statusMsg = "Error al cargar foto" }; return@Thread }
            val newMesh = SkinnedMesh(bitmap)
            runOnUiThread {
                pushUndo()
                // Liberar los bitmaps anteriores (TAREA 5: recycle)
                if (photo !== originalPhoto) safeRecycle(photo)
                safeRecycle(originalPhoto)
                photo = bitmap
                originalPhoto = bitmap
                mesh = newMesh
                animProgress = 0f
                showExportBtn = true
                statusMsg = "Foto cargada · se dobla con el muñeco"
                drawPreview()
            }
        }.start()
    }

    private fun loadBackground(uri: Uri) {
        Thread {
            val bmp = decodeScaled(uri, 1280)
            if (bmp != null) {
                runOnUiThread {
