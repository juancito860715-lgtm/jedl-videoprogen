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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectManagerSheet(
    repository: ProjectRepository,
    currentProject: ProjectDocument?,
    onCreate: (String) -> Unit,
    onSave: (ProjectDocument) -> Unit,
    onOpen: (ProjectDocument) -> Unit,
    onChange: (ProjectDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val projects = remember { mutableStateListOf<ProjectRepository.ProjectSummary>() }
    var projectName by remember { mutableStateOf("") }
    var layerText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    fun refresh() {
        projects.clear()
        projects.addAll(repository.list())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14151A)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Proyectos", color = Color(0xFFF5B841), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Guardado local en el teléfono · sin cuenta ni nube", color = Color(0xFFE7E8EE))
            OutlinedTextField(
                value = projectName,
                onValueChange = { projectName = it },
                label = { Text("Nombre del proyecto") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = {
                try {
                    onCreate(projectName)
                    projectName = ""
                    error = ""
                    refresh()
                } catch (e: Exception) { error = e.message ?: "No se pudo crear el proyecto" }
            }, modifier = Modifier.fillMaxWidth()) { Text("Crear proyecto") }

            if (currentProject != null) {
                Text("Abierto: ${currentProject.name}", color = Color(0xFFF5B841), fontWeight = FontWeight.SemiBold)
                Button(onClick = {
                    try { onSave(currentProject); error = ""; refresh() }
                    catch (e: Exception) { error = e.message ?: "No se pudo guardar" }
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar cambios") }

                OutlinedTextField(
                    value = layerText,
                    onValueChange = { layerText = it },
                    label = { Text("Texto para una nueva capa") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    if (layerText.isNotBlank()) {
                        val scene = currentProject.scenes.first()
                        val layer = LayerDocument(
                            name = layerText.take(48),
                            type = LayerType.TEXT,
                            text = layerText.take(500),
                            zIndex = (scene.layers.maxOfOrNull { it.zIndex } ?: -1) + 1
                        )
                        val changed = currentProject.copy(
                            modifiedAtMillis = System.currentTimeMillis(),
                            scenes = listOf(scene.copy(layers = scene.layers + layer)) + currentProject.scenes.drop(1)
                        )
                        onChange(changed)
                        layerText = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Añadir capa de texto") }
            }

            if (error.isNotBlank()) Text(error, color = Color(0xFFFF8A80))
            Spacer(Modifier.height(4.dp))
            Text("Proyectos guardados", color = Color(0xFFF5B841), fontWeight = FontWeight.SemiBold)
            if (projects.isEmpty()) Text("Todavía no hay proyectos guardados.", color = Color(0xFFE7E8EE))
            projects.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF1B1D24), RoundedCornerShape(10.dp))
                        .clickable {
                            try { onOpen(repository.load(item.id)); error = "" }
                            catch (e: Exception) { error = e.message ?: "No se pudo abrir el proyecto" }
                        }.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${item.sceneCount} escenas", color = Color(0xFFE7E8EE), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Abrir", color = Color(0xFFF5B841))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
