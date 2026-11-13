package com.jedl.videoprogen

import androidx.compose.foundation.background
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
fun ProjectTimelineSheet(
    project: ProjectDocument,
    onChange: (ProjectDocument) -> Unit,
    onImportMedia: (TimelineTrackType) -> Unit,
    onDismiss: () -> Unit
) {
    var subtitle by remember { mutableStateOf("") }
    val scene = project.scenes.first()
    fun addTrack(type: TimelineTrackType) {
        if (scene.timelineTracks.none { it.type == type }) {
            val updatedScene = scene.copy(timelineTracks = scene.timelineTracks + TimelineTrackDocument(
                name = when (type) {
                    TimelineTrackType.VIDEO -> "Vídeo"
                    TimelineTrackType.AUDIO -> "Audio"
                    TimelineTrackType.VOICE -> "Voz"
                    TimelineTrackType.MUSIC -> "Música"
                    TimelineTrackType.TEXT -> "Texto"
                    TimelineTrackType.SUBTITLE -> "Subtítulos"
                    TimelineTrackType.EFFECT -> "Efectos"
                },
                type = type
            ))
            onChange(project.copy(scenes = listOf(updatedScene) + project.scenes.drop(1)))
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14151A)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Línea de tiempo audiovisual", color = Color(0xFFF5B841), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Pistas independientes guardadas dentro del proyecto.", color = Color(0xFFE7E8EE))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onImportMedia(TimelineTrackType.VIDEO) }, modifier = Modifier.weight(1f)) { Text("Importar vídeo") }
                Button(onClick = { onImportMedia(TimelineTrackType.AUDIO) }, modifier = Modifier.weight(1f)) { Text("Importar audio") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onImportMedia(TimelineTrackType.VOICE) }, modifier = Modifier.weight(1f)) { Text("Importar voz") }
                Button(onClick = { onImportMedia(TimelineTrackType.MUSIC) }, modifier = Modifier.weight(1f)) { Text("Importar música") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { addTrack(TimelineTrackType.VOICE) }, modifier = Modifier.weight(1f)) { Text("Pista vacía de voz") }
                Button(onClick = { addTrack(TimelineTrackType.MUSIC) }, modifier = Modifier.weight(1f)) { Text("Pista vacía de música") }
            }
            OutlinedTextField(
                value = subtitle,
                onValueChange = { subtitle = it },
                label = { Text("Texto del subtítulo") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = {
                if (subtitle.isNotBlank()) {
                    val current = project.scenes.first()
                    val existing = current.timelineTracks.firstOrNull { it.type == TimelineTrackType.SUBTITLE }
                    val track = existing ?: TimelineTrackDocument(name = "Subtítulos", type = TimelineTrackType.SUBTITLE)
                    val clip = TimelineClipDocument(
                        name = "Subtítulo " + (track.clips.size + 1),
                        startMillis = 0L,
                        durationMillis = minOf(3_000L, current.durationMillis),
                        text = subtitle.trim().take(500)
                    )
                    val changedTrack = track.copy(clips = track.clips + clip)
                    val tracks = if (existing == null) current.timelineTracks + changedTrack
                        else current.timelineTracks.map { if (it.id == existing.id) changedTrack else it }
                    onChange(project.copy(scenes = listOf(current.copy(timelineTracks = tracks)) + project.scenes.drop(1)))
                    subtitle = ""
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Añadir subtítulo a la pista") }
            Spacer(Modifier.height(4.dp))
            Text("Pistas del proyecto", color = Color(0xFFF5B841), fontWeight = FontWeight.SemiBold)
            if (scene.timelineTracks.isEmpty()) Text("Aún no hay pistas.", color = Color(0xFFE7E8EE))
            scene.timelineTracks.forEach { track ->
                Column(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1D24), RoundedCornerShape(10.dp)).padding(12.dp)
                ) {
                    Text(track.name + " · " + track.type.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("${track.clips.size} clips", color = Color(0xFFE7E8EE), style = MaterialTheme.typography.bodySmall)
                    track.clips.forEach { clip ->
                        Text("• ${clip.name} · ${clip.startMillis} ms · ${clip.durationMillis} ms", color = Color(0xFFE7E8EE))
                        if (clip.text.isNotBlank()) Text(clip.text, color = Color(0xFFF5B841))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
