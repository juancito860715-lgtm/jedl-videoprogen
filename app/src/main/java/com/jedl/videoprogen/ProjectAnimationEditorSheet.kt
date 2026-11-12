package com.jedl.videoprogen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectAnimationEditorSheet(
    project: ProjectDocument,
    onChange: (ProjectDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val scene = project.scenes.first()
    var selectedClipId by remember(project.id) { mutableStateOf(scene.animationClips.firstOrNull()?.id ?: "") }
    var selectedTime by remember(project.id) { mutableStateOf(0L) }
    var timeText by remember(project.id) { mutableStateOf("0") }
    var boneName by remember(project.id) { mutableStateOf("armUR") }
    var angleText by remember(project.id) { mutableStateOf("0") }
    var error by remember(project.id) { mutableStateOf("") }
    val selectedClip = scene.animationClips.firstOrNull { it.id == selectedClipId }
    val selectedFrame = selectedClip?.keyframes?.firstOrNull { it.timeMillis == selectedTime }

    fun commitScene(updated: SceneDocument) {
        val saved = project.copy(scenes = listOf(updated) + project.scenes.drop(1))
        val validation = ProjectDocumentValidator.validate(saved)
        if (validation != null) error = validation else {
            onChange(saved)
            error = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14151A)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Editor de poses y keyframes", color = Color(0xFFF5B841), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Cada pose conserva los ángulos del rig. Los cambios se guardan en el proyecto.", color = Color(0xFFE7E8EE))
            Button(onClick = {
                val character = scene.layers.firstOrNull { it.type == LayerType.CHARACTER }
                    ?: LayerDocument(name = "Personaje", type = LayerType.CHARACTER)
                val layers = if (scene.layers.any { it.id == character.id }) scene.layers
                    else scene.layers + character
                val clip = AnimationClipDocument(
                    name = "Animación " + (scene.animationClips.size + 1),
                    targetLayerId = character.id,
                    durationMillis = scene.durationMillis,
                    keyframes = listOf(
                        AnimationKeyframeDocument(0L, mapOf("torso" to 0f)),
                        AnimationKeyframeDocument(scene.durationMillis, mapOf("torso" to 0f))
                    )
                )
                commitScene(scene.copy(layers = layers, animationClips = scene.animationClips + clip))
                selectedClipId = clip.id
                selectedTime = 0L
                timeText = "0"
            }, modifier = Modifier.fillMaxWidth()) { Text("Crear animación para el personaje") }

            if (scene.animationClips.isEmpty()) {
                Text("Todavía no hay clips de animación guardados.", color = Color(0xFFE7E8EE))
            }
            scene.animationClips.forEach { clip ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(
                        if (clip.id == selectedClipId) Color(0xFF34302A) else Color(0xFF1B1D24),
                        RoundedCornerShape(9.dp)
                    ).clickable {
                        selectedClipId = clip.id
                        selectedTime = clip.keyframes.firstOrNull()?.timeMillis ?: 0L
                        timeText = selectedTime.toString()
                    }.padding(10.dp)
                ) {
                    Text(clip.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${clip.keyframes.size} poses · ${clip.durationMillis} ms", color = Color(0xFFE7E8EE))
                }
            }

            if (selectedClip != null) {
                Text("Fotogramas de «${selectedClip.name}»", color = Color(0xFFF5B841), fontWeight = FontWeight.SemiBold)
                selectedClip.keyframes.forEach { frame ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(
                            if (frame.timeMillis == selectedTime) Color(0xFF34302A) else Color(0xFF1B1D24),
                            RoundedCornerShape(8.dp)
                        ).clickable {
                            selectedTime = frame.timeMillis
                            timeText = frame.timeMillis.toString()
                            angleText = (frame.boneAngles[boneName] ?: 0f).toString()
                        }.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pose · ${frame.timeMillis} ms", color = Color.White)
                        Text("${frame.boneAngles.size} articulaciones", color = Color(0xFFE7E8EE))
                    }
                }
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tiempo de la pose (ms)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = boneName,
                    onValueChange = { boneName = it.filter { ch -> ch.isLetterOrDigit() }.take(64) },
                    label = { Text("Articulación (ej. armUR, foreArmR, thighL)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = angleText,
                    onValueChange = { angleText = it.take(16) },
                    label = { Text("Ángulo en grados (-360 a 360)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val time = timeText.toLongOrNull()
                        val angle = angleText.toFloatOrNull()
                        if (time == null || time !in 0L..selectedClip.durationMillis || boneName.isBlank() ||
                            angle == null || !angle.isFinite() || angle !in -360f..360f) {
                            error = "Revisa el tiempo y el ángulo introducidos"
                        } else {
                            try {
                                val changed = ProjectAnimationEditorOperations.upsertPose(
                                    selectedClip, selectedTime, time, boneName, angle
                                )
                                commitScene(scene.copy(animationClips = scene.animationClips.map { if (it.id == changed.id) changed else it }))
                                selectedTime = time
                            } catch (failure: IllegalArgumentException) {
                                error = failure.message ?: "No se pudo actualizar la pose"
                            }
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Crear / actualizar pose") }
                    Button(onClick = {
                        try {
                            val changed = ProjectAnimationEditorOperations.deletePose(selectedClip, selectedTime)
                            commitScene(scene.copy(animationClips = scene.animationClips.map { if (it.id == changed.id) changed else it }))
                            selectedTime = changed.keyframes.first().timeMillis
                            timeText = selectedTime.toString()
                        } catch (failure: IllegalArgumentException) {
                            error = failure.message ?: "No se pudo eliminar la pose"
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Eliminar pose") }
                }
            }
            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80))
            Spacer(Modifier.height(24.dp))
        }
    }
}
